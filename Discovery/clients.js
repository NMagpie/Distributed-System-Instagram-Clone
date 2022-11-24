const grpc = require("@grpc/grpc-js");
var protoLoader = require("@grpc/proto-loader");

const GW_PATH = "./proto/gateway.proto";

const CH_PATH = "./proto/cache.proto";

const options = {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
};

var grpcObj_gw = protoLoader.loadSync(GW_PATH, options);

var grpcObj_ch = protoLoader.loadSync(CH_PATH, options);

const GatewayService = grpc.loadPackageDefinition(grpcObj_gw).services.gateway.GatewayService;

const CacheService = grpc.loadPackageDefinition(grpcObj_ch).services.cache.CacheService;

module.exports = {
    GatewayService,
    CacheService
};