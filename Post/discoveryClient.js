const grpc = require("@grpc/grpc-js");
var protoLoader = require("@grpc/proto-loader");
const PROTO_PATH = "./proto/discovery.proto";

const discoveryHost = process.env.DISCOVERY_HOST;

const discoveryPort = process.env.DISCOVERY_PORT;

const options = {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
};

var grpcObj = protoLoader.loadSync(PROTO_PATH, options);

const DiscoveryService = grpc.loadPackageDefinition(grpcObj).services.discovery.DiscoveryService;

const client = new DiscoveryService(
    `${discoveryHost}:${discoveryPort}`,
    grpc.credentials.createInsecure()
);

module.exports = client;