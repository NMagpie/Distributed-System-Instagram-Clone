const { MongoClient } = require("mongodb");

const loginData = process.env.DB_LOGIN;

const hostname = process.env.DB_HOSTNAME;
const port = process.env.DB_PORT;

const uri = `mongodb://${loginData}${hostname}:${port}/?authMechanism=DEFAULT`;

const client = new MongoClient(uri);

const database = client.db('discovery');

module.exports = database

const eventTypes = [`exit`, `SIGINT`, `SIGUSR1`, `SIGUSR2`, `uncaughtException`, `SIGTERM`];

eventTypes.forEach((eventType) => {
    process.on(eventType, () => {
        client.close();
        process.exit(1);
    });
});