var express = require('express');
var app = express();
app.use(express.static(__dirname + '/public'));
app.listen(9003);

const mysql = require('mysql2');

const con = mysql.createConnection({
    host: "localhost",
    user: "root",
    password: "123",
    database: "post_db",
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
    getProfile: (username, callback) => {
        const profUsername = username.request.username;

        con.query(`SELECT username, name, profilePicture, postQuantity FROM post_db.profiles WHERE username = \'${profUsername}\'`, function (err, result, fields) {
        
            if (err) throw err;

            callback(null, result[0]);
        });
    },

    getPost: (postParams, callback) => {

        const {username, postN} = postParams.request

        con.query(`SELECT username, photo, text FROM post_db.posts WHERE username = \'${username}\' AND postN = \'${postN}\'`, function (err, result, fields) {
        
            if (err) throw err;

            callback(null, result[0]);
        });
    },
});

postServer.bindAsync(
    "localhost:9002",
    grpc.ServerCredentials.createInsecure(),
    (error, port) => {
        console.log("Server running at localhost:"+port);
        postServer.start();
    }
);