require('dotenv').config();

const database = require("./db");

const GatewayService = require("./gwClient").GatewayService;

const services = {
    gateway: [],
    auth: [],
    cache: [],
    post: [],
    discovery: null,
};

const emptyServices = services;

const initialServices = async () => {
    const DBservices = database.collection('services');

    const options = {
        projection: {_id: 0, type: 1, hostname: 1, port: 1,},
    }

    const cursor = DBservices.find({}, options);

    cursor.forEach((service) => {

        services[service.type].push(service);

    });
}

initialServices();

const hostname = process.env.HOSTNAME;
const port = process.env.PORT;

const grpc = require("@grpc/grpc-js");
const protoLoader = require("@grpc/proto-loader");
const PROTO_PATH = "./proto/discovery.proto";

const loaderOptions = {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
};

var packageDef = protoLoader.loadSync(PROTO_PATH, loaderOptions);

const discServer = new grpc.Server();

const grpcObj = grpc.loadPackageDefinition(packageDef);

discServer.addService(grpcObj.services.discovery.DiscoveryService.service, {
    discover: discover,
    removeService: removeService,
    getStatus: getStatus,
});

discServer.bindAsync(
    `${hostname}:${port}`,
    grpc.ServerCredentials.createInsecure(),
    (error, port) => {
        console.log("[ " + getCurrentTime() + " ]:\t" + `Server running at ${hostname}:${port}`);

        discServer.start();
    }
);

function getStatus(empty, callback) {
    callback(null, `Server Type: discovery\nHostname: ${hostname}\nPort: ${port}`);
}

const discResponse = (service) => {
    if (service.type === "gateway") {
        return services
    } else 
        return emptyServices
}

function discover(serviceInfo, callback) {

    const service = serviceInfo.request;

    if (
        services.hasOwnProperty(service.type) &&
        !services[service.type].some((includedService) => {
            return includedService.type === service.type && 
            includedService.hostname === service.hostname && 
            includedService.port === service.port
        })
        ) {
        (async () => {
            const DBservices = database.collection('services');

            const result = await DBservices.insertOne(service);

            console.log("[ " + getCurrentTime() + " ]:\t"
            + `Service ${service.type}:${service.hostname}:${service.port} was added: ${result.acknowledged}`);

            if (service.type === 'cache' && services.cache.length) {
                callback({message: "Cache service already exists, if it is down, wait a while and try reloading it again."});
                return;
            }

            if (service.type === "gateway") {
                service.client = new GatewayService(
                    `${service.hostname}:${service.port}`,
                    grpc.credentials.createInsecure()
                );
            } else {
                if (services.gateway.length && services.gateway[0]?.client) {
                    services.gateway[0].client.newService(serviceInfo.request, (error, result) => {
                        if (error) console.error(error);
                    });
                }
            }

            services[service.type].push(service);

            callback(null, discResponse(service));
        })()
    } else {
        console.log("[ " + getCurrentTime() + " ]:\tSuch service type does not exist, or this object already exists in the database!");

        callback(null, discResponse(service));
    }
}

function removeService(serviceInfo, callback) {

    const service = serviceInfo.request;

    (async () => {
        const DBservices = database.collection('services');
    
        const result = await DBservices.deleteOne(service);
    
        console.log("[ " + getCurrentTime() + " ]:\t" + `Service was removed: ${result.acknowledged}`);
    
        services[service.type] = services[service.type].filter((selectedService) => {
            return (selectedService.type != service.type && selectedService.hostname != service.hostname && selectedService != service.port);
        });
    
        callback(null, {});
    })()

}

function getCurrentTime() {
    const now = new Date();

    return now.getHours() + ":" + now.getMinutes() + ":" + now.getSeconds();
}