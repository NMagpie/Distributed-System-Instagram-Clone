const grpc = require("@grpc/grpc-js");
var protoLoader = require("@grpc/proto-loader");
const PROTO_PATH = "./auth.proto";

const options = {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
};
var grpcObj = protoLoader.loadSync(PROTO_PATH, options);

const AuthenticationService = grpc.loadPackageDefinition(grpcObj).authentication.AuthenticationService;

const client = new AuthenticationService(
    "localhost:9001",
    grpc.credentials.createInsecure()
);

module.exports = client;