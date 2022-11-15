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

EXPOSE 7999

#CMD [ "npm", "run", "dockerRun"]

CMD [ "node", "-r", "dotenv/config", ".", "dotenv_config_path=./.env.docker" ]