/*
Note: this is a modified version pulled from @blandman's NPM version. A few changes have been made and pull requests are pending.
    Once those are merged in, we'll roll back to just using the package.
*/


const fetch = require('node-fetch'), ws = require('ws'), EventEmitter = require('events');


const apiURL = 'https://api.sense.com/apiservice/api/v1/'

var emmitter = new EventEmitter();

var senseWS = null;

var verbose = true;

const setupWS = (onData) => {
    try {
        const sendData = typeof onData == 'function'
        let WSURL = `wss://clientrt.sense.com/monitors/${authData.monitors[0].id}/realtimefeed?access_token=${authData.access_token}`
        senseWS = new ws(WSURL)    

        senseWS.on('open', () => {
            if(sendData) {
                onData({
                    status: "Connected"
                })
            }
        });
        senseWS.on('message', (data) => {
            emmitter.emit('data', JSON.parse(data));            
            if(sendData) {
                onData({
                    status: "Received",
                    data: JSON.parse(data)
                })
            } else {
                if (verbose){console.log(data);}
            }
        });
        senseWS.onclose = (data) => {
            emmitter.emit('close', data);        
            if (verbose){console.log("Connection closed: " + data);}
        };
        senseWS.onerror = (data) => {
            emmitter.emit('error',data);
            if (verbose){console.log("Error: " + data);}
        };
    } catch (error) {
        console.log(error);
    }    
}

var authData = {};


module.exports = 
    
    //Constructor
    async (config, onData) => {
    return new Promise( async (resolve, reject) => {
        try {
            if(!config.email || !config.password) {
                throw new Error('Config missing required parameters, needs email and password (optional base64)')
            }
            if(Buffer.from(config.password).toString('base64') === config.password) {
                config.password = Buffer.from(config.password.toString('base64'))
            }
            if (config.verbose != undefined){verbose = config.verbose};
    
            if (config.access_token){
                authData = {"access_token":config.access_token, user_id: config.user_id, authorized: true, monitors: [{id:33218}]}
            }
            else{
                authData = await doAuth();
                //authData = await (await fetch(`${apiURL}authenticate`, { method: 'POST', body: `email=${config.email}&password=${config.password}`, headers: {"Content-Type":"application/x-www-form-urlencoded"} })).json()
            }

            async function doAuth(){
                return new Promise( async (resolve, reject) => {
                    const res = await fetch(`${apiURL}authenticate`, { method: 'POST', body: `email=${config.email}&password=${config.password}`, headers: {"Content-Type":"application/x-www-form-urlencoded"} })
                    if (res.status == 200){
                        resolve(await res.json());
                    }
                    else if (res.status == 401){
                        reject('Authentication failed! Check username/password and try again.');
                    }
                })
            }
    
            //Only proceed if auth succeeded
            if(authData.authorized) {
                if(typeof onData == 'function') {
                    onData({
                        status: 'Authenticated',
                        data: authData.monitors
                    })
                }

                //Utility function for all callout methods; handles 401's with a special reject so client can reliably catch it
                async function doSenseCallout (URL) {
                    return new Promise( async (resolve, reject) => {
                        const res = await fetch(URL, { method: 'GET', headers: {"Authorization": `bearer ${authData.access_token}`} })
                        if (res.status == 200){
                            resolve(await res.json());
                        }
                        else if (res.status == 401){
                            reject('Authentication failed!');
                        }
                        else{
                            reject(await res.json())
                        }
                    })
                }

                //Final resolve - list of exposed methods
                resolve({
                                    
                    authData: authData,
                    events: emmitter,
                    
                    openStream: () => {
                        setupWS(onData);
                    },

                    closeStream: () => {
                        senseWS.close();
                    },
                    
                    getAuth: async () => {                        
                        authData = await (await fetch(`${apiURL}authenticate`, { method: 'POST', body: `email=${config.email}&password=${config.password}`, headers: {"Content-Type":"application/x-www-form-urlencoded"} })).json()   
                        return authData
                    },
                    
                    getDevices: async () => {
                        return doSenseCallout(`${apiURL}app/monitors/${authData.monitors[0].id}/devices`);},

                    getMonitorInfo: async () => {                        
                        return doSenseCallout(`${apiURL}app/monitors/${authData.monitors[0].id}/status`);
                    },
                    getTimeline: async () => {
                        return doSenseCallout(`${apiURL}users/${authData.user_id}/timeline`);                        
                    }
                })
            } else if(authData.status == 'error') {                
                reject(new Error(authData.error_reason));
            } else {                
                reject(new Error('Unable to make auth request'));
            }
        } catch (error) {
            reject(error);
        }        
    });
}

