require('dotenv').config();

const crypto = require('crypto');

const hostname = process.env.HOSTNAME;
const port = process.env.PORT;
const httpPort = process.env.HTTPPORT;

const dbHost = process.env.DBHOST;
const dbUser = process.env.DBUSER;
const dbPassword = process.env.DBPASSWORD;
const database = process.env.DB;

const promisify = require("./util");

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
    console.log("[ " + getCurrentTime() + " ]: \tDB Connected!");
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

postServer.addService(grpcObj.post.PostService.service, {
    getProfile: getProfile,
    getPost: getPost,
    getStatus: getStatus,
    putPost: putPost,
    putProfile : putProfile,
    putPicture: putPicture,
});

postServer.bindAsync(
    `${hostname}:${port}`,
    grpc.ServerCredentials.createInsecure(),
    (error, port) => {
        console.log("[ " + getCurrentTime() + " ]: \t" + `Server running at ${hostname}:${port}`);
        postServer.start();
    }
);

(async function () {
    await discoveryClient.discover({type: "post", hostname: hostname, port: port}, (error, result) => {
        if (error) console.error(error);
    })
}());

function getProfile(username, callback) {

    console.log("[ " + getCurrentTime() + " ]: {getProfile}\t" + profUsername);

    const profUsername = username.request.username;

    query(`SELECT username, name, profilePicture FROM post_db.profiles WHERE username = \'${profUsername}\'`)
    .then((result, _) => {
        callback(null, result[0]);
    })
    .catch(error => console.error(error));

}

function getPost(postParams, callback) {

    console.log("[ " + getCurrentTime() + " ]: {getPost}\t" + postParams.request);

    const {username, dozen} = postParams.request

    query(`SELECT username, photo, text FROM post_db.posts WHERE username = \'${username}\' LIMIT 10 OFFSET ${dozen*10}`)
    .then((result, _) => {
        callback(null, {postInfo: result,});
    })
    .catch(error => console.error(error));

}

function getStatus(empty, callback) {

    console.log("[ " + getCurrentTime() + " ]: {getStatus}");

    const status = {
        message: `Server Type: post\nHostname: ${hostname}\nPort: ${port}`
    };

    callback(null, status);
}

function putPost(postInfo, callback) {

    console.log("[ " + getCurrentTime() + " ]: {putPost}\t" + postInfo.request);

    const key = postInfo.request.key;

    const photo = postInfo.request.photo;

    const text = postInfo.request.text;

    sendMessage({method: "whoIsThis", body: JSON.stringify({key: key})})
    .then(response => {
        const username = JSON.parse(response.body).username;
        if (username == 'null') {

            console.log("[ " + getCurrentTime() + " ]:\tUser data was rejected.");

            return ({ success: false, error: "User data was rejected." });

        } else {
            console.log("[ " + getCurrentTime() + " ]:\tUser data was accepted.");

            query(`INSERT INTO post_db.posts (username, photo, text) VALUES ('${username}', '${photo}', '${text}')`)
        }
    })
    .then((result) => {
        if (result?.error)
            callback(null, {success: false, error: result.error});
        else
            callback( null, {success: true,})
        })
    .catch(error => {console.error(error)});

}

function putProfile(profileInfo, callback) {

    console.log("[ " + getCurrentTime() + " ]: {putProfile}\t" + profileInfo.request);

    const username = profileInfo.username;

    const name = profileInfo.name;

    const avatar = profileInfo.avatar;

    query(`INSERT INTO post_db.profiles (username, name, avatar) VALUES ('${username}', '${name}', '${avatar}')`)
    .then((result) => {
        if (result?.error)
            callback(null, {success: false, error: result.error});
        else
            callback( null, {success: true,})
        })
    .catch(error => {console.error(error)});
}

function putPicture(call, callback) {

    console.log("[ " + getCurrentTime() + " ]: {putPicture}");

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
                console.log(error);
                callback(null, {success: false, error: error});
            }

            callback(null, {success: true, link: `http://${hostname}:${httpPort}/images/${filename}`});
        });
    })
}

const query = promisify(con.query.bind(con));

const sendMessage = promisify(discoveryClient.sendMessage.bind(discoveryClient));

function getCurrentTime() {
    const now = new Date();

    return now.getHours() + ":" + now.getMinutes() + ":" + now.getSeconds();
}