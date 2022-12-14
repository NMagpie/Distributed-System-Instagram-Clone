require('dotenv').config();

const database = require("./db");

const GatewayService = require("./clients").GatewayService;

const CacheService = require("./clients").CacheService;

const logger = require("./logger");

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
        logger.info(`Server running at ${hostname}:${port}`);

        discServer.start();
    }
);

function getStatus(empty, callback) {
    callback(null, `Server Type: discovery\nHostname: ${hostname}\nPort: ${port}`);
}

const discResponse = (service) => {
    switch (service) {
        case "gateway":
            return services

        case "cache":
            return {
                gateway: [],
                auth: [],
                cache: services.cache,
                post: [],
                discovery: null,
            }

        default:
            return emptyServices
    }
}

function discover(serviceInfo, callback) {

    const service = serviceInfo.request;

    if (process.platform === "linux")
    service.hostname = serviceInfo.getPeer().split(":")[0];

    if (service.hostname === "") service.hostname = "127.0.0.1"

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

            logger.info(`Service ${service.type}:${service.hostname}:${service.port} was added: ${result.acknowledged}`);

            switch (service.type) {
                case "gateway":
                    service.client = new GatewayService(
                        `${service.hostname}:${service.port}`,
                        grpc.credentials.createInsecure()
                    );
                    break;
                case "cache":
                    service.client = new CacheService(
                        `${service.hostname}:${service.port}`,
                        grpc.credentials.createInsecure()
                    );

                    services.cache.forEach((cService => cService.client.newService(serviceInfo.request, (error, result) => {
                        if (error) logger.info(error);
                    })));

                default:
                    if (services.gateway.length && services.gateway[0]?.client) {

                        services.gateway[0].client.newService(serviceInfo.request, (error, result) => {
                            if (error) logger.info(error);
                        });
                    };
            }

            services[service.type].push(service);

            callback(null, discResponse(service));
        })()
    } else {
        logger.info("Such service type does not exist, or this object already exists in the database!");

        callback(null, discResponse(service));
    }
}

function removeService(serviceInfo, callback) {

    const service = serviceInfo.request;

    (async () => {
        const DBservices = database.collection('services');
    
        const result = await DBservices.deleteOne(service);

        logger.info(`Service was removed: ${result.acknowledged}`);
    
        services[service.type] = services[service.type].filter((selectedService) => {
            return (selectedService.type != service.type && selectedService.hostname != service.hostname && selectedService != service.port);
        });
    
        callback(null, {});
    })()

}