'use strict';
module.exports = {
    email: '',
    password: '',
    smartThingsHubIP: '10.0.0.200',
    smartThingsAppId: '', //Optional: Can be used to restrict SmartThings app to listening for certain Sense Stream server
    autoReconnect: true, // Attempt to reconnect to sense service of dropped connections
    callbackPort: 9021 // Port used by the web server to receive settings changes from ST
};
