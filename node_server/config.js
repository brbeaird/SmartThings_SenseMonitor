'use strict';
module.exports = {
    email: process.env.senseEmail,
    password: process.env.sensePassword,
    smartThingsHubIP: process.env.smartthingsHubIP,
    smartThingsAppId: process.env.smartthingsAppId, //Optional: Can be used to restrict SmartThings app to listening for certain Sense Stream server
    autoReconnect: process.env.autoReconnect, // Attempt to reconnect to sense service of dropped connections
    callbackPort: process.env.port // Port used by the web server to receive settings changes from ST
};

