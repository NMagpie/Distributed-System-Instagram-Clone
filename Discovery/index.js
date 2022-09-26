require('dotenv').config();

const promisify = require("./util").promisify;

const database = require("./db");

const services = {
    gateway: [],
    auth: [],
    cache: [],
    post: [],
    message: [],
}

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
const PROTO_PATH = "./discovery.proto";

const loaderOptions = {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
};

var packageDef = protoLoader.loadSync(PROTO_PATH, loaderOptions);

const postServer = new grpc.Server();

const grpcObj = grpc.loadPackageDefinition(packageDef);
postServer.addService(grpcObj.DiscoveryService.service, {
    getStatus: getStatus,
    discover: discover,
    removeService: removeService,
});

postServer.bindAsync(
    `${hostname}:${port}`,
    grpc.ServerCredentials.createInsecure(),
    (error, port) => {
        console.log(`Server running at ${hostname}:${port}`);
        postServer.start();
    }
);

function getStatus(empty, callback) {

    const status = {
        message: `Server Type: post\nHostname: ${hostname}\nPort: ${port}`
    };

    callback(null, status);
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
        
            console.log(`Service was added: ${result.acknowledged}`);
        
            services[service.type].push(service);
        
            callback(null, {});
        })()
    } else {
        console.log('Such service type does not exist, or this object already exists in the database!');

        callback(null, {});
    }
}

function removeService(serviceInfo, callback) {

    const service = serviceInfo.request;

    (async () => {
        const DBservices = database.collection('services');
    
        const result = await DBservices.deleteOne(service);
    
        console.log(`Service was removed: ${result.acknowledged}`);
    
        services[service.type] = services[service.type].filter((selectedService) => {
            return (selectedService.type != service.type && selectedService.hostname != service.hostname && selectedService != service.port);
        });
    
        callback(null, {});
    })()

}