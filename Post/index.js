require('dotenv').config();

const crypto = require('crypto');

const logger = require("./logger");

const hostname = process.env.HOSTNAME;
const port = process.env.PORT;
const httpPort = process.env.HTTPPORT;

const dbHost = process.env.DBHOST;
const dbUser = process.env.DBUSER;
const dbPassword = process.env.DBPASSWORD;
const database = process.env.DB;

const {promisify, taskLimiter} = require("./util");

const discoveryClient = require("./discoveryClient");

const fs = require('fs');

var express = require('express');
var app = express();
app.use(express.static('public'));
app.listen(httpPort);

const mysql = require('mysql2');

const con = mysql.createConnection({
    host: dbHost,
    user: dbUser,
    password: dbPassword,
    database: database,
});

con.connect(function(err) {
    if (err) throw err;
    logger.info("DB Connected!");
});

const grpc = require("@grpc/grpc-js");
const protoLoader = require("@grpc/proto-loader");
const PROTO_PATH = "./proto/post.proto";

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

postServer.addService(grpcObj.services.post.PostService.service, {
    getProfile: taskLimiter(getProfile, postServer),
    getPost: taskLimiter(getPost, postServer),
    getStatus: taskLimiter(getStatus, postServer),
    putPost: taskLimiter(putPost, postServer),
    putProfile : taskLimiter(putProfile, postServer),
    putPicture: taskLimiter(putPicture, postServer),
    commit: taskLimiter(commit, postServer),
    rollback: taskLimiter(rollback, postServer),
});

postServer.bindAsync(
    `${hostname}:${port}`,
    grpc.ServerCredentials.createInsecure(),
    (error, port) => {
        logger.info(`Server running at ${hostname}:${port}`);
        postServer.start();
    }
);

(async function () {
    await discoveryClient.discover({type: "post", hostname: hostname, port: port}, (error, result) => {
        if (error) logger.info(error);
    })
}());

function getProfile(username, callback) {

            const profUsername = username.request.username;

            logger.info(`{getProfile}\t${username}`);

            //example if service is sending the result for too long

            //new Promise(resolve => setTimeout(resolve, 1_000)).then(() => {

                query(`(SELECT username, name, profilePicture FROM post_db.profiles WHERE username = \'${profUsername}\') UNION ALL SELECT NULL, NULL, NULL LIMIT 1`)
                .then((result, _) => {

                    if (result[0].username != null)
                        callback(null, result[0]);
                    else
                        callback({
                            message: "The user does not exists!",
                        });

                })
                .catch(error => {
                    logger.info(error);
                });

            //})
}

function getPost(postParams, callback) {

            const {username, dozen} = postParams.request;

            logger.info(`{getPost}\t${username}\t${dozen}`);

            query(`(SELECT username, photo, text FROM post_db.posts WHERE username = \'${username}\' LIMIT 10 OFFSET ${dozen*10}) UNION ALL SELECT NULL, NULL, NULL LIMIT 10`)
            .then((result, _) => {

                if (result[0].username != null)
                    callback(null, {postInfo: result,});
                else
                    callback({
                        message: "The user does not exists!",
                    });

            })
            .catch(error => {
                logger.info(error);
            });

}

function getStatus(empty, callback) {

            //example if service is sending the result for too long

            //new Promise(resolve => setTimeout(resolve, 1_500)).then(() => {

            logger.info("{getStatus}");

            const status = {
                message: `Server Type: post\nHostname: ${hostname}\nPort: ${port}`
            };

            callback(null, status);

            //})

}

function putPost(postInfo, callback) {

            const photo = postInfo.request.photo;

            const text = postInfo.request.text;

            const username = postInfo.request.username;

            logger.info(`{putPost}\t${username}`);

            query(`INSERT INTO post_db.posts (username, photo, text) VALUES ('${username}', '${photo}', '${text}')`)
            .then((result) => {
                if (result?.error)
                    callback(null, {success: false, error: result.error});
                else
                    callback( null, {success: true,})
                })
            .catch(error => {
                logger.info(error);
                callback(null, {success: false, error: error});
            });

}

const cache = new Map();

function putProfile(profileInfo, callback) {

    cache.set(profileInfo.request.id, profileInfo.request);

    const username = profileInfo.request.username;

    logger.info(`{putProfile}\t${username}`);

    query(`SELECT EXISTS(SELECT 1 FROM post_db.profiles WHERE username ='${username}') as result`)
    .then((result) => {
        if (result?.error)
            return callback(null, {success: false, error: result.error});
        if (result[0].result == 1)
            return callback(null, {success: false, error: "User already exists."});
        else
            return callback( null, {success: true,});
        })
    .catch(error => {
        logger.info(error);
        callback(null, {success: false, body: error});
    });

}

function commit(idRequest, callback) {

    const profileInfo = cache.get(idRequest.request.id);

    cache.delete(idRequest.request.id);

    const username = profileInfo.username;

    const name = profileInfo.name;

    const avatar = profileInfo.avatar;

    logger.info(`{commit}\t${idRequest.request.id}`);

    query(`INSERT INTO post_db.profiles (username, name, profilePicture) VALUES ('${username}', '${name}', '${avatar}')`)
    .then((result) => {
        if (result?.error)
            callback(null, {});
        else
            callback( null, {});
        })
    .catch(error => {
        logger.info(error);
        callback(null, {});
    });
}

function rollback(idRequest, callback) {

    logger.info(`{rollback}\t${idRequest.request.id}`);

    cache.delete(idRequest.request.id);

    callback( null, {} );

}

function putPicture(call, callback) {

    logger.info("{putPicture}");

    var pictureData = [];

    var pictureType = "";

    call.on('data', function(dataStream) {

        pictureData.push(dataStream.chunk);

        if (dataStream.fileType)
            pictureType = dataStream.fileType;
    })

    call.on('end', () => {

        var combinedData = Buffer.concat(pictureData);

        const filename = crypto.createHash('sha256').update( pictureData[0].slice(0,100) + pictureType).digest('hex') + Date.now().toString() + pictureType;

        fs.writeFile(`./public/images/${filename}`, combinedData, 'binary', (error) => {

            if (error) {
                logger.info(error);
                callback(null, {success: false, error: error});
                return;
            }

            callback(null, {success: true, link: `http://${hostname}:${httpPort}/images/${filename}`});
        });
    })
}

const query = promisify(con.query.bind(con));