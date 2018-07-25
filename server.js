const sense = require('unofficial-sense')

sense({
    email: "email",    
    password: "password"
}, (data) => {
    
    //console.log(data) 
    if (data.status == "Received"){
        if (data.data != undefined){
            if (data.data.payload != undefined){
                if (data.data.payload.devices != undefined){
                    for (let dev of data.data.payload.devices){
                        console.log(dev.name + ": " + dev.w);
                    }
                }                
            }
        }        
    }
    
    //real time data stream from your sense monitor
})