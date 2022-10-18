require('dotenv').config();

const promisify = require("./util").promisify;

const database = require("./db");

const serviceConstructors = require("./proto");

const services = {
    gateway: [],
    auth: [],
    cache: [],
    post: [],
    discovery: [],
}

const initialServices = async () => {
    const DBservices = database.collection('services');

    const options = {
        projection: {_id: 0, type: 1, hostname: 1, port: 1,},
    }

    const cursor = DBservices.find({}, options);

    cursor.forEach((service) => {

        const constructor = constructByType.get(service.type);

        service.client = new constructor(
            service.hostname+":"+service.port,
            grpc.credentials.createInsecure(),
        );

        service.load = 0;

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

discServer.addService(grpcObj.discovery.DiscoveryService.service, {
    discover: discover,
    removeService: removeService,
    sendMessage: sendMessage,
    putPicture: putPicture,
});

discServer.bindAsync(
    `${hostname}:${port}`,
    grpc.ServerCredentials.createInsecure(),
    (error, port) => {
        console.log("[ " + getCurrentTime() + " ]:\t" + `Server running at ${hostname}:${port}`);

        discServer.start();
    }
);

function getStatus() {
    return `Server Type: discovery\nHostname: ${hostname}\nPort: ${port}`;
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

            const constructor = constructByType.get(service.type);

            service.client = new constructor(
                service.hostname+":"+service.port,
                grpc.credentials.createInsecure()
            );

            service.load = 0;
        
            services[service.type].push(service);
        
            callback(null, {});
        })()
    } else {
        console.log("[ " + getCurrentTime() + " ]:\tSuch service type does not exist, or this object already exists in the database!");

        callback(null, {});
    }
}

const constructByType = new Map([
    [ 'gateway', serviceConstructors.GatewayService ],
    [ 'auth', serviceConstructors.AuthenticationService ],
    [ 'post', serviceConstructors.PostService ],
    [ 'cache', serviceConstructors.CacheService ],
]);

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

const messageTypes = new Map([
    [ 'isAuth', 'auth' ],     //Auth
    [ 'auth', 'auth' ],
    [ 'register', 'auth' ],
    [ 'whoIsThis', 'auth' ],

    [ 'getProfile', 'post' ], //PostService
    [ 'getPost', 'post' ],
    [ 'putPost', 'post' ],  
    [ 'putProfile', 'post' ],
    ]);

const minLoadService = (sType) => {

    if (services[sType].length == 0)
        return null;

    return services[sType].reduce((prev, curr) => {
        return prev.load < curr.load ? prev : curr;
    });
}


function sendMessage(message, callback) {

    const {method, body} = message.request;

    console.log("[ " + getCurrentTime() + " ]: {" + method + "}\t" + body);

    if (method === "getStatus") {
        const [sType, sHostname, sPort] = body.split(":");

        if (sType == null || hostname == null || port == null) {
            callback(null, {success: false, body: "No such service! Check the input data!"});
            return;
        }

        try{

        if (sType === "discovery" && hostname === sHostname && port === sPort) {
            callback(null, {success: true, body: getStatus()});
            return;
        }} catch(error) {console.log(error);}

        const service = services[sType].filter(service => service.hostname === sHostname && service.port == sPort)[0];

        if (service == null) {
            callback(null, {success: false, body: "No such service!"});
            return;
        } else {
            if (service.client == null) {
                callback(null, {success: false, body: "No such service!"});
                return;
            }
        }

        service.load++;

        service.client.getStatus(null, (error, result) => {

            service.load--;

            if (error) {
                callback(null, {success: false, body: error});
                return;
            }

            callback(null, {success: true, body: JSON.stringify(result)});
        });

    } else {

        if (method == "getProfile" || method == "getPost") {

            const parsedBody = JSON.parse(body);

            const query = {
                method: 'get',
                message: JSON.stringify({
                what: method,
                of: parsedBody.username,
                ...(parsedBody.dozen != null && {dozen: parsedBody.dozen}),
            }),
            };
        
            const service = minLoadService('cache');
        
            if (service == null || !service.hasOwnProperty('client')) {
                callback(null, {success: false, body: 'No such service available!'});
                return;
            }
        
            service.load++;
        
            service.client.query(query,
            (error, result) => {
        
            service.load--;
        
            if (error || result.message == "null") {
                sendFurther(method, body, callback);
                return;
            }

            callback(null, {success: true, body: JSON.stringify(result)});

            });

        } else
            sendFurther(method, body, callback);

    }

}

function sendFurther(method, body, callback) {

    const sType = messageTypes.get(method);

    const service = minLoadService(sType);

    if (service == null || !service.hasOwnProperty('client')) {
        callback(null, {success: false, body: 'No such service available!'});
        return;
    }

    service.load++;

    const parsedBody = JSON.parse(body);

    service.client[method](parsedBody,
    (error, result) => {

    service.load--;

    if (error) {
        callback(null, {success: false, body: error});
        return;
    }

    if (method == "getProfile" || method == "getPost") {
        result.username = parsedBody.username;
        putCache(parsedBody.username, method, result, parsedBody.dozen);
    }

    callback(null, {success: true, body: JSON.stringify(result)});

    });

}

function putCache(username, method, body, dozen = null) {
    const message = (method == "getProfile") ? 
    {
        username: username,
        name: body.name,
        profilePicture: body.profilePicture,
        posts: new Map(),
    } : {
        username: username,
        name: null,
        profilePicture: null,
        posts: { [dozen]: body.postInfo },
    }

    const query = {
        method: 'put',
        message: JSON.stringify(message)
    };

    const service = minLoadService('cache');

    if (service == null || !service.hasOwnProperty('client')) {
        callback(null, {success: false, body: 'No such service available!'});
        return;
    }

    service.load++;

    service.client.query(query,
    (resolve, _) => {
    service.load--;
    });
}

function putPicture(call, callback) {

    console.log("[ " + getCurrentTime() + " ]: {putPicture}");

    const post = minLoadService('post');

    const callPost = post.client.putPicture(function(error, response) {

        if (error) {
            callback(null, {success: response.success, error: error});
            return;
        }

        callback(null, {success: response.success, link: response.link});
    });

    call.on('data', function(dataStream) {

        if (dataStream.fileType) {
                callPost.write({fileType: dataStream.fileType});
                callPost.end();
        } else
            callPost.write({chunk: dataStream.chunk});

    });

    call.on('end', function() {})
}

function getCurrentTime() {
    const now = new Date();

    return now.getHours() + ":" + now.getMinutes() + ":" + now.getSeconds();
}