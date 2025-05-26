/*
    Mapped Device Filter
    Copyright 2025 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

definition (
    parent: "evequefou:Filtered Device Mirror",
    name: "Mapped Device",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Mirror one capability of a device with mapping logic",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-device-mirror/main/mapped-device.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Mapped Device Mirror", install: true, uninstall: true) {
        section("General") {
            input "thisName", "text", title: "Name this Mapped Device", required: true, defaultValue: app.getLabel(), submitOnChange: true
            if(thisName) app.updateLabel("$thisName")
        }
        section("Device Selection") {
            input "outputCapability", "enum",
                options: parent.getDeviceTypes().collectEntries { [(it.capability): it.type] },
                title: "Capability to produce", required: true, multiple: false, submitOnChange: true

            [["first", true], ["second", false]].each { device ->
                def devicePrefix = device[0]
                def deviceKey = "${device[0]}Device"
                def deviceLabel = "${device[0].capitalize()} input device"
                def required = device[1]

                input deviceKey, "capability.*", title: deviceLabel, required: required, multiple: false, submitOnChange: true

                if(settings[deviceKey] ) {
                    def inputDevice = settings[deviceKey]
                    def attributeKey = "${devicePrefix}AttributeName"
                    def acceptableAttributes = inputDevice.getSupportedAttributes()?.findAll{ ["NUMBER", "ENUM"].contains(it.dataType) };
                    if (acceptableAttributes?.size() > 1) {
                        input attributeKey, "enum",
                            options: acceptableAttributes.collect{ it.name },
                            title: "Attribute to consider", required: true, multiple: false, submitOnChange: true
                    } else if (acceptableAttributes?.size() == 1) {
                        app.updateSetting(attributeKey, acceptableAttributes[0].name)
                        paragraph "Input device ${inputDevice} has only one usable attribute (${acceptableAttributes[0].name})"
                    }
                    else {
                        log.warn "Input device ${inputDevice} has no acceptable attributes!"
                        paragraph "Input device ${inputDevice} has no acceptable attributes!"
                        app.clearSetting(attributeKey);
                    }

                    if( settings[attributeKey] ) {
                        def attribute = acceptableAttributes.find { it.name == settings[attributeKey] }
                        switch(attribute.dataType) {
                            case "NUMBER":
                                // Need to build a list of splitpoints
                                def splitpointCount = state["${devicePrefix}SplitpointCount"]
                                if( !splitpointCount ) {
                                    state["${devicePrefix}SplitpointCount"] = splitpointCount = 0
                                }
                                (0..splitpointCount).each { i ->
                                    def previousSplitpointKey = "${devicePrefix}_Splitpoint_${i-1}"
                                    def splitpointKey = "${devicePrefix}_Splitpoint_${i}"
                                    def nextSplitpointKey = "${devicePrefix}_Splitpoint_${i+1}"
                                    debug "Splitpoint ${i} (${splitpointKey}) of ${splitpointCount}"
                                    if( i < splitpointCount ) {
                                        def lowerBound = i == 0 ? "*" : (settings[previousSplitpointKey] ?: "*")
                                        def upperBound = i == splitpointCount ? "*" : (settings[nextSplitpointKey] ?: "*")
                                        def range = "${lowerBound}..${upperBound}";
                                        debug "Range for ${splitpointKey} is ${range}"
                                        input splitpointKey, "decimal", title: splitpointToRange(devicePrefix, i, false),
                                            range: range, required: true, submitOnChange: true, width: 6
                                    } else {
                                        paragraph splitpointToRange(devicePrefix, i, true), width: 6
                                    }
                                    input "Divide_${splitpointKey}", "button", title: "Split", submitOnChange: true, width: 3
                                    if( i < splitpointCount ) {
                                        input "Delete_${splitpointKey}", "button", title: "Delete", submitOnChange: true, width: 3
                                    }
                                }
                                break;
                            case "STRING":
                                // Similar, but for list of strings
                                break;
                            case "ENUM":
                                // Nothing to do for ENUM
                                break;
                            default:
                                log.warn "Input attribute ${inputDevice}.${attribute} has an unsupported type (${attribute.dataType})"
                                paragraph "Input device ${inputDevice} has an unsupported attribute type (${attribute.dataType})"
                                app.clearSetting(attributeKey);
                                break;
                        }
                    }
                }
            }
        }
        if( firstDevice && firstAttributeName && outputCapability ) {
            def properties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties
            properties.each { prop ->
                section("How to set ${prop}") {
                    def outputAttribute = getChildDevice().getSupportedAttributes().find { it.name == prop }

                    /*
                        TODO: Non-enum types will come later

                        If the inputs are enums, just build a selector for each combination.
                        If a given device is a number, first have splitpoints,
                        then build a selector for each range.
                        If a given device is a string, assemble a list of
                        possible values and build a selector for each, plus
                        "anything else"

                        If outputs are strings, have a text box (support
                        variables?)

                        If outputs are numbers, support:
                        - Fixed
                        - Variable plus fixed offset
                        - Input (if number) plus fixed/variable/other-input offset
                        */

                    def firstValues = getValues("first")
                    def secondValues = getValues("second")
                    def outputValues = (outputAttribute?.getValues() ?: []) + [UNCHANGED];

                    for (def firstValue in firstValues) {
                        def firstKey = firstValue[0]
                        def firstDisplay = firstValue[1]
                        int numOptions = Math.max(secondValues.size(), 1);
                        int width = Math.max(Math.floor(12.0 / numOptions), 1);

                        for (def secondValue in secondValues) {
                            def secondKey = secondValue[0]
                            def secondDisplay = secondValue[1]

                            def heading = "When ${firstDevice} ${firstAttributeName} is ${firstDisplay}"
                            if( secondValue != null ) {
                                heading += " and ${secondDevice} ${secondAttributeName} is ${secondDisplay}"
                            }
                            heading += "..."

                            input constructKey(prop, firstKey, secondKey), "enum", options: outputValues, width: width,
                                title: heading, submitOnChange: true, required: true
                        }
                    }
                }
            }
        }
    }
}

// Returns array of pairs, key name and display string
def getValues(devicePrefix) {
    def attribute = settings["${devicePrefix}Device"]?.getSupportedAttributes()?.find { it.name == settings["${devicePrefix}AttributeName"] }

    if( !attribute ) {
        return [null]
    }

    switch(attribute.dataType) {
        case "NUMBER":
            // For NUMBER, we need to build a list of splitpoints
            // and return the values for each splitpoint
            def splitpointCount = state["${devicePrefix}SplitpointCount"]
            return (0..splitpointCount).collect { i ->
                ["range${i}", splitpointToRange(devicePrefix, i, true)]
            }
        case "STRING":
            // TODO: Support string values
            return []
        case "ENUM":
            // For ENUM, the value is the same as the display name
            return attribute.getValues().collect { [it, it] }
        default:
            log.warn "Input attribute ${devicePrefix} has an unsupported type (${attribute.dataType})"
            return []
    }
}

String splitpointToRange(devicePrefix, i, includeEndpoint) {
    def isLast = (i == state["${devicePrefix}SplitpointCount"])
    def value = settings["${devicePrefix}_Splitpoint_${i}"]
    def previous = settings["${devicePrefix}_Splitpoint_${i-1}"]
    def result = ""

    value = value && value % 1 == 0 ? value.toInteger() : value
    previous = previous && previous % 1 == 0 ? previous.toInteger() : previous

    if( i == 0 && isLast ) {
        // No splitpoints
        result = "any value"
    } else if( isLast ) {
        result = "greater than ${previous ?: "something"}"
    } else if( i == 0 ) {
        result = "less than"
        if( includeEndpoint ) {
            result += " ${value ?: "something"}"
        }
    } else {
        result = "from ${previous ?: "one thing"} to"
        if( includeEndpoint ) {
            result += " ${previous ?: "another"}"
        }
    }
    return result
}

void appButtonHandler(String button) {
    debug "Button pressed: ${button}"
    def components = button.split("_")
    if( components.size() != 4 ) {
        log.warn "Invalid button name ${button}"
        return
    }
    def action = components[0]
    if( !["Delete", "Divide"].contains(action) ) {
        log.warn "Invalid action ${action} in button name ${button}"
        return
    }

    def devicePrefix = components[1]
    if( !["first", "second"].contains(devicePrefix) ) {
        log.warn "Invalid device prefix ${devicePrefix} in button name ${button}"
        return
    }

    def index = components[3].toInteger()

    if (outputCapability) {
        def outputProperties = parent.getDeviceTypes().find { it.capability == outputCapability }?.properties
        if (!outputProperties) {
            log.warn "No properties found for output capability: ${outputCapability}"
            return
        }
    } else {
        log.warn "Output capability is not set"
        return
    }

    def firstOffset = devicePrefix == "first" ?
        action == "Divide" ? 1 : -1 : 0;
    def secondOffset = devicePrefix == "second" ?
        action == "Divide" ? 1 : -1 : 0;

    if( action == "Divide" ) {
        state["${devicePrefix}SplitpointCount"] = state["${devicePrefix}SplitpointCount"] + 1
    }

    def firstValues = getValues("first").collect{it[0]}
    def secondValues = getValues("second").collect{it[0]}

    // --- Data structure to hold dynamic info for first/second ---
    def deviceContext = [
        "first": [
            values: firstValues,
            offset: firstOffset,
            range: null // Will be populated
        ],
        "second": [
            values: secondValues,
            offset: secondOffset,
            range: null // Will be populated
        ]
    ]

    // --- Loop to reduce duplication for range calculation ---
    ["first", "second"].each { currentPrefix ->
        def currentValues = deviceContext[currentPrefix].values
        def currentRange

        if (devicePrefix == currentPrefix) { // This is the device being affected by the action
            if (action == "Divide") {
                // Divide: Shift elements from 'index' onwards UP by 1.
                // Source: i, Dest: i+1
                // Iterate from last element down to index.
                currentRange = (currentValues.size() - 1)..index // Inclusive range
            } else { // action == "Delete"
                // Delete: Shift elements from 'index' + 1 onwards DOWN by 1.
                // Source: i, Dest: i-1
                // Iterate from index + 1 up to size - 1.
                currentRange = (index + 1)..(currentValues.size() - 1)
            }
        } else { // This is the other device, it iterates over its full existing range.
            currentRange = (0)..(currentValues.size() - 1)
        }
        currentRange = currentRange.toList() // Ensure it's a list for consistent iteration

        deviceContext[currentPrefix].range = currentRange
        debug "${currentPrefix.capitalize()} range: ${currentRange}"
    }

    // Extract the calculated ranges back to their original variables for clarity in loops below
    def firstRange = deviceContext.first.range
    def secondRange = deviceContext.second.range
    def outputProperties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties

    debug "First values: ${firstValues}"
    debug "Second values: ${secondValues}"
    debug "Output properties: ${outputProperties}"
    debug "Iterating ${devicePrefix} ${index} (firstRange: ${firstRange} and secondRange: ${secondRange})"

    outputProperties.each { out ->
        firstRange.each { i ->
            debug "First source index ${i}"
            def firstSourceValue = firstValues[i]
            def firstDestIndex = i + firstOffset

            def firstDestValue
            try {
                firstDestValue = firstValues[firstDestIndex]
            } catch (IndexOutOfBoundsException e) {
                firstDestValue = null
                debug "Warning: firstDestIndex ${firstDestIndex} was out of bounds for firstValues. Assuming null."
            }

            secondRange.each { j ->
                debug "Second source index ${j}"
                def secondSourceValue = secondValues[j]
                def secondDestIndex = j + secondOffset

                def secondDestValue
                try {
                    secondDestValue = secondValues[secondDestIndex]
                } catch (IndexOutOfBoundsException e) {
                    secondDestValue = null
                    debug "Warning: secondDestIndex ${secondDestIndex} was out of bounds for secondValues. Assuming null."
                }

                def sourceKey = constructKey(out, firstSourceValue, secondSourceValue)
                def data = getValue(out, firstSourceValue, secondSourceValue)
                def destKey = constructKey(out, firstDestValue, secondDestValue)
                debug "Copying ${sourceKey} to ${destKey} (${data})"

                if( data != null ) {
                    app.updateSetting(destKey, data)
                } else {
                    app.clearSetting(destKey)
                }
            }
        }
    }
    if( action == "Delete" ) {
        state["${devicePrefix}SplitpointCount"] = state["${devicePrefix}SplitpointCount"] - 1
    }
}

void updated() {
	unsubscribe()
    cleanup()
	initialize()
}

void cleanup() {
    def firstValues = getValues("first").collect{it[0]}
    def secondValues = getValues("second").collect{it[0]}
    def outputProperties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties

    def goodKeys = outputProperties.collect { out ->
        firstValues.collect { it1 ->
            secondValues.collect { it2 ->
                constructKey(out, it1, it2)
            }
        }
    }.flatten()

    debug "Good keys: ${goodKeys}"

    def toRemove = settings.keySet().findAll { it.startsWith("condition_")} - goodKeys
    debug "Removing keys: ${toRemove}"

    toRemove.each { app.clearSetting(it) }

    ["first", "second"].each { devicePrefix ->
        def splitpointCount = state["${devicePrefix}SplitpointCount"].toInteger() ?: 0
        debug "Cleaning up splitpoints for ${devicePrefix} with count ${splitpointCount}"
        // Remove splitpoints that are no longer needed
        settings.keySet().findAll { it.startsWith("${devicePrefix}_Splitpoint_") }.each { key ->
            def index = key.split("_").last().toInteger()
            if( index >= splitpointCount ) {
                debug "Removing splitpoint ${key} as it is no longer needed"
                app.clearSetting(key)
            }
        }
    }

}

void installed() {
	initialize()
}

void uninstalled() {
    def dni = "Filtered-" + app.id.toString()
    parent.removeChildDevice(dni)
}

private getChildDevice() {
    def dni = "Filtered-" + app.id.toString()
    def type = parent.getDeviceTypes().find { it.capability == outputCapability }
    def existing = parent.fetchChildDevice(dni, thisName, type.namespace, type.driver)
    def capabilityName = outputCapability - "capability."
    capabilityName = capabilityName.capitalize()

    if( existing.hasCapability(capabilityName) ) {
        return existing
    } else {
        log.info "Child device ${existing} does not have capability ${outputCapability}"
        parent.removeChildDevice(dni)
        return parent.fetchChildDevice(dni, thisName, type.namespace, type.driver)
    }
}

void initialize() {
    [[firstDevice,firstAttributeName], [secondDevice,secondAttributeName]].each {
        def device = it[0];
        def attribute = it[1];
        debug "Subscribing to ${device} ${attribute}"

        if( device && attribute ) {
            subscribe(device, attribute, "updateState")
        }
    }
    updateState();
}

void updateState(evt = null) {
    def source = evt?.device;
    def property = evt?.name;
    def value = evt?.value;
    def description = evt?.descriptionText

    if( evt ) {
        debug "Received ${property} ${value} from ${source} ${description ?: ""}"
        description = description ?: " ${source} ${property} became ${value}"
    }

    def firstValue = firstDevice?.currentValue(firstAttributeName);
    def secondValue = secondDevice?.currentValue(secondAttributeName);
    def outputProperties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties

    if ( firstValue != null ) {
        if( firstDevice && firstAttributeName && outputCapability ) {
            def properties = parent.getDeviceTypes().find { it.capability == outputCapability }.properties
            getChildDevice().parse(outputProperties.collect {
                [
                    name: it,
                    value: settings[constructKey(it, firstValue, secondValue)],
                    descriptionText: description
                ]
            }.findAll{ it.value != UNCHANGED && it.value != null }
            );
        }
    }
}

private getValue(outputAttribute, firstValue, secondValue = null) {
    def key = constructKey(outputAttribute, firstValue, secondValue);
    return settings[key];
}

private constructKey(outputAttribute, firstValue, secondValue = null) {
    def key = "${outputAttribute}-${firstValue}"
    if( secondValue != null ) {
        key += "-${secondValue}"
    }
    return key
}

void refresh() {
    initialize()
}

void debug(String msg) {
    if( parent.debugEnabled() ) {
        log.debug msg
    }
}

@Field static final String UNCHANGED = "(unchanged)"
