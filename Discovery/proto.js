const grpc = require("@grpc/grpc-js");
var protoLoader = require("@grpc/proto-loader");

const AUTH_PATH = "./proto/auth.proto";
const GW_PATH = "./proto/gateway.proto";
const POST_PATH = "./proto/post.proto";
const CACHE_PATH = "./proto/cache.proto";

const options = {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
};

var grpcObj_auth = protoLoader.loadSync(AUTH_PATH, options);

const AuthenticationService = grpc.loadPackageDefinition(grpcObj_auth).authentication.AuthenticationService;

var grpcObj_gw = protoLoader.loadSync(GW_PATH, options);

const GatewayService = grpc.loadPackageDefinition(grpcObj_gw).gateway.GatewayService;

var grpcObj_post = protoLoader.loadSync(POST_PATH, options);

const PostService = grpc.loadPackageDefinition(grpcObj_post).post.PostService;

var grpcObj_cache = protoLoader.loadSync(CACHE_PATH, options);

const CacheService = grpc.loadPackageDefinition(grpcObj_cache).cache.CacheService;

module.exports = {
    AuthenticationService,
    GatewayService,
    PostService,
    CacheService,
};