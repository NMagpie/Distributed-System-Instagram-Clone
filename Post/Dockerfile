#FROM node:16

FROM node:16-alpine

# Create app directory
WORKDIR /usr/src/app

# Install app dependencies
# A wildcard is used to ensure both package.json AND package-lock.json are copied
# where available (npm@5+)
COPY package*.json ./

RUN npm install

COPY . .

EXPOSE 9001

EXPOSE 9002

#CMD [ "node", "index.js"]

CMD [ "node", "-r", "dotenv/config", ".", "dotenv_config_path=./.env.docker" ]