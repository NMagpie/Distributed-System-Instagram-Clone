require('dotenv').config();

const hostname = process.env.HOSTNAME;
const port = process.env.PORT;

const dbHost = process.env.DBHOST;
const dbUser = process.env.DBUSER;
const dbPassword = process.env.DBPASSWORD;
const database = process.env.DB;

const authClient = require("./authProto");

var express = require('express');
var app = express();
app.use(express.static(__dirname + '/public'));
app.listen(9003);

const mysql = require('mysql2');

const con = mysql.createConnection({
    host: dbHost,
    user: dbUser,
    password: dbPassword,
    database: database,
});

con.connect(function(err) {
    if (err) throw err;
    console.log("DB Connected!");
});

const grpc = require("@grpc/grpc-js");
const protoLoader = require("@grpc/proto-loader");
const PROTO_PATH = "./post.proto";

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
postServer.addService(grpcObj.PostService.service, {
    getProfile: getProfile,
    getPost: getPost,
    getStatus: getStatus,
    putPost: putPost,
});

postServer.bindAsync(
    `${hostname}:${port}`,
    grpc.ServerCredentials.createInsecure(),
    (error, port) => {
        console.log(`Server running at ${hostname}:${port}`);
        postServer.start();
    }
);

function getProfile(username, callback) {

    const profUsername = username.request.username;

    query(`SELECT username, name, profilePicture FROM post_db.profiles WHERE username = \'${profUsername}\'`)
    .then((result, _) => {
        callback(null, result[0]);
    })
    .catch(error => console.error(error));

}

function getPost(postParams, callback) {

    const {username, dozen} = postParams.request

    query(`SELECT username, photo, text FROM post_db.posts WHERE username = \'${username}\' LIMIT 10 OFFSET ${dozen*10}`)
    .then((result, _) => {
        callback(null, {postInfo: result,});
    })
    .catch(error => console.error(error));

}

function getStatus(empty, callback) {

    const status = {
        message: `Server Type: post\nHostname: ${hostname}\nPort: ${port}`
    };

    callback(null, status);
}

function putPost(postInfo, callback) {

    const key = postInfo.request.key;

    const photo = postInfo.request.photo;

    const text = postInfo.request.text;

    whoIsThis({key: key})
    .then(response => {
        const username = response.username;
        if (username == 'null') {

            console.log("User data was rejected.");

            return ({ success: false, error: "User data was rejected." });

        } else {
            console.log("User data was accepted.");

            query(`INSERT INTO post_db.posts (username, photo, text) VALUES ('${username}', '${photo}', '${text}')`)
        }
    })
    .then((result) => {
        if (result?.error)
            callback(null, result);
        else
            callback( null, {success: true,})
        })
    .catch(error => {console.error(error)});

}

function promisify(fn) {
    return function(...args) {
        return new Promise((resolve, reject) => {
            fn(...args, (error, data) => {
                if (error) return reject(error);

                resolve(data);
            });
        });
    };
}

const query = promisify(con.query.bind(con));

const whoIsThis = promisify(authClient.whoIsThis.bind(authClient));