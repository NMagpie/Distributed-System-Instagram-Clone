var net = require('net');

var logHost = 'localhost',
    logPort = 9503;

var conn = net.createConnection({host: logHost, port: logPort})
.on('error', function(err) {
  console.error(err);
});

function getCurrentTime(now) {
  return now.getHours() + ":" + now.getMinutes() + ":" + now.getSeconds();
}

const newMessage = (message, time) => {
  return {
    '@timestamp': time.toISOString(),
    'message': message,
    'sType': "discovery",
    'hostname': process.env.HOSTNAME,
    'port': process.env.PORT,
  }
}

function info(message) {
  const time = new Date();

  console.log("[ " + getCurrentTime(time) + " ]:\t" + message);

  conn.write( JSON.stringify ( newMessage(message, time) ) + "\r\n" );
}

module.exports = {
  info,
};
