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

function getCurrentTime() {
    const now = new Date();

    return now.getHours() + ":" + now.getMinutes() + ":" + now.getSeconds();
}

const limit = 25;

function taskLimiter(method, postServer) {
    return function(body, callback) {

        const activeSessions = postServer.callTracker.callsStarted - (postServer.callTracker.callsSucceeded + postServer.callTracker.callsFailed);

        console.log(limit + " " + activeSessions);

        if (activeSessions > limit) {
            console.log("[ " + getCurrentTime() + ` ]: {${method.name}}\tConcurrent task limit exceeded!`);

            callback({
                message: '429 Too many requests',
            });

        return;
        }

        method(body, callback);
    }
}

module.exports = {
    promisify,
    getCurrentTime,
    taskLimiter,
}