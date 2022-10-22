const grpc = require("@grpc/grpc-js");
var protoLoader = require("@grpc/proto-loader");

const GW_PATH = "./proto/gateway.proto";

const options = {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
};

var grpcObj_gw = protoLoader.loadSync(GW_PATH, options);

const GatewayService = grpc.loadPackageDefinition(grpcObj_gw).services.gateway.GatewayService;

module.exports = {
    GatewayService
};