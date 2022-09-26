module.exports = {
    promisify: function promisify(fn) {
    return function(...args) {
        return new Promise((resolve, reject) => {
            fn(...args, (error, data) => {
                if (error) return reject(error);

                resolve(data);
            });
        });
    };
},
}