/**
 *  Light Physical Button Setup
 *
 *  Copyright 2018 Eliot Stocker
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Light Physical Button Setup",
    namespace: "piratemedia/smartthings",
    author: "Eliot Stocker",
    description: "Application to enable direction of lights based on physical/virtual Buttons",
    category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/eliotstocker/SmartThings-LightPhysicalControl/master/logo-small.png",
    iconX2Url: "https://raw.githubusercontent.com/eliotstocker/SmartThings-LightPhysicalControl/master/logo.png",
    parent: "piratemedia/smartthings:Light Physical Control",
)


preferences {
    page(name: "LightSettingsPage")
}

def LightSettingsPage() {
    dynamicPage(name: "LightSettingsPage", install: true, uninstall: true) {
    	section("Select the button you wish to use") {
            input "button", "capability.button", title: "Button"
        }
        section("Select Light(s) to turn on/off") {
            input "lights", "capability.switch", title: "Lights", multiple: true, submitOnChange: true
        }
    
        section("Select Lights initial Settings when turned on") {
            if(canControlLevel()) {
                input "level", "number", title: "Light Brightness", range: "(1..100)", required: false
            }
            if(canControlColorTemperature()) {
                input "temp", "number", title: "Light Color Temperature", range: "(2700..6500)", required: false
            }
            if(canControlColor()) {
                input "color", "enum", title: "Color", options: ["Red", "Orange", "Yellow", "Green", "Blue", "Indigo", "Violet"], required: false
            }
        }
        
        section("Time specific settings (Overrides above values within selected time frames)") {
        }
        section() {
        	app(name: "timeSetup", appName: "Light Physical Button Time Settings", namespace: "piratemedia/smartthings", title: "New Time Specific Setting", multiple: true)
        }
        
        section("Lighting Setup Name") {
        	label title: "Setup Name", required: true, defaultValue: app.label
        }
    }
}

def checkForCapability(capability) {
	def found = false
    lights.each { light ->
        def capabilites = light.getCapabilities()
        capabilites.each {cap ->
            if(cap.name == capability) {
                found = true
            }
        }
    }
    return found
}

def checkDeviceForCapabilityById(id, capability) {
	def selected;
    lights.each { light ->
    	if(light.id == id) {
        	selected = light
        }
    }
    if(selected != null) {
    	return checkDeviceForCapability(selected, capability);
    }
    return false;
}

def checkDeviceForCapability(dev, capability) {
	def found = false
	def capabilites = dev.getCapabilities()
    capabilites.each {cap ->
        if(cap.name == capability) {
            found = true
        }
    }
    return found
}

def canControlLevel() {
	return checkForCapability('Switch Level')
}

def canControlColorTemperature() {
	return checkForCapability('Color Temperature')
}

def canControlColor() {
	return checkForCapability('Color Control')
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(button, "button", buttonPress)
}

def buttonPress(evt) {
	def anyOn = false;
	for(light in lights) {
    	if(light.currentValue("switch") == "on") anyOn = true;
    }
    if(anyOn) {
    	lights.off()
    } else {
        def timedSettingsActive = false;
        def children = getChildApps()
        children.each { child ->
        	if(child.isActive()) {
            	timedSettingsActive = true;
                def settings = child.getSettings()
                if(child.hasSpecificSettings()) {
                    lights.each{ light ->
                        def data = child.getSpecificLightSetting(light.label);
                        if(data != null) {
                            log.debug "Settings for: $light.label: $data"
                        	def lightOff = false
                            if(data.on != null && data.on.toString() != "null") {
                                if(!data.on) {
                                    log.debug "turn light off: $data.light"
                                    light.off()
                                    lightOff = true;
                                }
                            }
                            if(!lightOff) {
                                light.on()
                                if(data.level != null && data.level.toString() != "null") {
                                    runIn(1, setupLevel, [data: data])
                                }
                                if(data.temp != null && data.temp.toString() != "null") {
                                    runIn(2, setupColorTemp, [data: data])
                                }
                                if(data.color != null && data.color.toString() != "null") {
                                    runIn(3, setupColor, [data: data])
                                }
                            }
                        } else {
                            light.on()
                        	settings['light'] = light.label
                            log.debug "No Settings for: $light.label: $settings"
                            if(settings.level != null && settings.level.toString() != "null") {
                            	runIn(1, setupLevel, [data: settings])
                            }
                            if(settings.temp != null && settings.temp.toString() != "null") {
                                runIn(2, setupColorTemp, [data: settings])
                            }
                            if(settings.color != null && settings.color.toString() != "null") {
                                runIn(3, setupColor, [data: settings])
                            }
                        }
                    }
                } else {
                    lights.on()
                    if(settings.level != null && settings.level.toString() != "null") {
                        runIn(1, setupLevel, [data: settings])
                    }
                    if(settings.temp != null && settings.temp.toString() != "null") {
                        runIn(2, setupColorTemp, [data: settings])
                    }
                    if(settings.color != null && settings.color.toString() != "null") {
                        runIn(3, setupColor, [data: settings])
                    }
                }
                return
            }
		}
        
        if(!timedSettingsActive) {
            lights.on()
        	if(level != null) {
            	runIn(1, setupLevel, [data: [level: level, temp: temp, color: color]])
            }
            if(temp != null) {
            	runIn(1, setupColorTemp, [data: [level: level, temp: temp, color: color]])
            }
            if(color != null) {
            	runIn(1, setupColor, [data: [level: level, temp: temp, color: color]])
            }
        }
    }
}

def setupColorTemp(data) {
    if(data.temp != null && data.temp.toString() != "null") {
    	if(data.light != null && data.light.toString() != "null") {
        	def light = getDeviceByLabel(data.light)
        	if(!checkDeviceForCapability(light, 'Color Control') || data.color == null || data.color.toString() == "null") {
                try {
                    light.setColorTemperature(data.temp);
                } catch(e) {}
            }
        } else {
            for(light in lights) {
                if(!checkDeviceForCapability(light, 'Color Control') || data.color == null || data.color.toString() == "null") {
                    try {
                        light.setColorTemperature(data.temp);
                    } catch(e) {}
                }
            }
        }
    }
}

def setupLevel(data) {
    if(data.level != null && data.level.toString() != "null") {
    	if(data.light != null && data.light.toString() != "null") {
        	def light = getDeviceByLabel(data.light)
        	if(!checkDeviceForCapability(light, 'Color Control') || data.color == null || data.color.toString() == "null") {
                try {
                    light.setLevel(data.level);
                } catch(e) {}
            } else {
                log.debug "dont set color temp as were going to set the color"
            }
        } else {
            for(light in lights) {
                if(!checkDeviceForCapability(light, 'Color Control') || data.color == null || data.color.toString() == "null") {
                    try {
                        light.setLevel(data.level);
                    } catch(e) {}
                } else {
                    log.debug "dont set color temp as were going to set the color"
                }
            }
        }
    }
}

def setupColor(data) {
    if(data.color != null && data.color.toString() != "null") {
    	if(data.light != null && data.light.toString() != "null") {
        	def light = getDeviceByLabel(data.light)
        	try {
                def hue = 0;
                switch(data.color) {
                    case "Red":
                    hue = 0;
                    break;
                    case "Orange":
                    hue = 8.3;
                    break;
                    case "Yellow":
                    hue = 16;
                    break;
                    case "Green":
                    hue = 33;
                    break;
                    case "Blue":
                    hue = 66;
                    break;
                    case "Indigo":
                    hue = 77;
                    break;
                    case "Violet":
                    hue = 88;
                    break;
                }
                def lvl = data.level ?: 100
                light.setColor([
                    hue: hue, saturation: 100, level: lvl
                ]);
                log.debug "Set Temp to: $temp on: $light.name"
            } catch(e) {}
        } else {
            for(light in lights) {
                try {
                    def hue = 0;
                    switch(data.color) {
                        case "Red":
                        hue = 0;
                        break;
                        case "Orange":
                        hue = 8.3;
                        break;
                        case "Yellow":
                        hue = 16;
                        break;
                        case "Green":
                        hue = 33;
                        break;
                        case "Blue":
                        hue = 66;
                        break;
                        case "Indigo":
                        hue = 77;
                        break;
                        case "Violet":
                        hue = 88;
                        break;
                    }
                    def lvl = data.level ?: 100
                    light.setColor([
                        hue: hue, saturation: 100, level: lvl
                    ]);
                    log.debug "Set Temp to: $temp on: $light.name"
                } catch(e) {}
            }
        }
    }
}

def getLightDevices() {
	def vals = []
	lights.each{ light ->
    	def l = [
        id: light.id,
        label: light.label
        ]
    	vals.add(l)
    }
	return vals
}

def getDeviceByID(id) {
	def out = null
	lights.each { light ->
    	if(light.id == id) out = light
    }
    
    return out
}

def getDeviceByLabel(label) {
	def out = null
	lights.each { light ->
    	if(light.label == label) out = light
    }
    log.debug "get device by label: $lable, $out.id" 
    return out
}