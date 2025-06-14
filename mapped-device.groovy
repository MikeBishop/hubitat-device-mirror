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
                                def values = getValues(devicePrefix)
                                debug "Values for ${devicePrefix} are ${values.inspect()}"
                                values.eachWithIndex { value, i ->
                                    debug "Adding input for ${devicePrefix} ${value.keySlug} (${value.inspect()})"
                                    if( value.maxKey ) {
                                        def lowerBound = value.min ?: "*"
                                        def upperBound = value.max ?: "*"
                                        def range = "${lowerBound}..${upperBound}";
                                        debug "Range for ${value.maxKey} is ${range}"
                                        input value.maxKey, "decimal", title: value.displayTitle(),
                                            range: range, required: true, submitOnChange: true, width: 6
                                    } else {
                                        paragraph value.displayFull(), width: 6
                                    }
                                    input "Divide_${devicePrefix}_${i}", "button", title: "Split", submitOnChange: true, width: 3
                                    if( value.max ) {
                                        input "Delete_${devicePrefix}_${i}", "button", title: "Delete", submitOnChange: true, width: 3
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

                        If outputs are strings, have a text box (support
                        variables?) or UNCHANGED
                        */

                    def numVars = getGlobalVarsByType("NUMBER").collect { it.key };

                    def firstValues = getValues("first")
                    def secondValues = getValues("second")
                    def outputType = outputAttribute?.dataType;
                    def outputValues = [];
                    if( outputType == "ENUM" ) {
                        outputValues = (outputAttribute?.getValues() ?: []) + [UNCHANGED];
                    }
                    debug "Input values for first device are ${firstValues.inspect()}"
                    debug "Input values for second device are ${secondValues.inspect()}"
                    debug "Output values for ${prop} are ${outputValues.inspect()}"

                    for (def firstValue in firstValues) {
                        debug "First value is ${firstValue.inspect()}"
                        def firstKey = firstValue.keySlug
                        def firstDisplay = firstValue.displayFull()
                        debug "First key is ${firstKey}, display is ${firstDisplay}"
                        int numOptions = Math.max(secondValues.size(), 1);
                        int width = Math.max(Math.floor(12.0 / numOptions), 1);

                        for (def secondValue in secondValues) {
                            def secondKey = secondValue?.keySlug
                            def secondDisplay = secondValue?.displayFull()

                            def heading = "When ${firstDevice} ${firstAttributeName} is ${firstDisplay}"
                            if( secondValue != null ) {
                                heading += " and ${secondDevice} ${secondAttributeName} is ${secondDisplay}"
                            }
                            heading += "..."

                            switch (outputAttribute?.dataType) {
                                case "STRING":
                                case "NUMBER":
                                    def key = constructKey(prop, firstKey, secondKey)
                                    input constructKey(prop, firstKey, secondKey), "text",
                                        title: heading, defaultValue: '',
                                        width: width, submitOnChange: true
                                    break;
                                case "ENUM":
                                    input constructKey(prop, firstKey, secondKey), "enum", options: outputValues, width: width,
                                        title: heading, submitOnChange: true, required: true
                                    break;
                                default:
                                    log.warn "Output attribute ${prop} has an unsupported type (${outputAttribute.dataType})"
                                    paragraph "Output attribute ${prop} has an unsupported type (${outputAttribute.dataType})"
                                    app.clearSetting(constructKey(prop, firstKey, secondKey));
                                    break;
                            }
                        }
                    }
                    if( ["NUMBER", "STRING"].contains(outputAttribute?.dataType)) {
                        paragraph "Use %first% to refer to ${firstDevice} ${firstAttributeName}, " +
                                  "%second% to refer to ${secondDevice} ${secondAttributeName}, " +
                                  "and %current% to refer to the current output ${outputAttribute} value. " +
                                  "Use global variables like %varname%. " +
                                  "Escape percent signs with a backslash (\\%) if you want to use them literally."
                        paragraph "Basic math (+ - * /) is supported, e.g., %first% + 10 or %second% / 2. " +
                                  "You can use parentheses to control precedence, e.g., (%first% + %second%) * 2."
                        paragraph "Leave the field empty to leave the value unchanged."
                    }
                }
            }
        }
    }
}

// Returns array of objects, types varying by attribute type:
// - For all types, includes keySlug, displayTitle(), and displayFull()
// - For NUMBER, includes min, max, minKey, and maxKey for defining splitpoints
// - For ENUM and STRING, includes exact and matchAll
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
                def previousSplitpointKey = i > 0 ? "${devicePrefix}_Splitpoint_${i-1}" : null
                def splitpointKey = i < splitpointCount ? "${devicePrefix}_Splitpoint_${i}" : null
                def previousSplitpoint = settings[previousSplitpointKey]
                def splitpoint = settings[splitpointKey]
                [
                    keySlug: "range${i}",
                    min: previousSplitpoint,
                    max: splitpoint,
                    minKey: previousSplitpointKey,
                    maxKey: splitpointKey,
                    displayTitle: { rangeToDisplayString(previousSplitpoint, splitpoint, splitpointCount == 0, false) },
                    displayFull: { rangeToDisplayString(previousSplitpoint, splitpoint, splitpointCount == 0, true) },
                    matchAll: splitpointCount == 0
                ]
            }
        case "STRING":
            // TODO: Support string values
            return []
        case "ENUM":
            // For ENUM, the value is the same as the display name
            return attribute.getValues().collect {
                def title = it.toString()
                def titleClosure = { title }
                [
                    keySlug: it,
                    exact: it,
                    displayTitle: titleClosure,
                    displayFull: titleClosure,
                    matchAll: false
                ]
             }
        default:
            log.warn "Input attribute ${devicePrefix} has an unsupported type (${attribute.dataType})"
            return []
    }
}

static String rangeToDisplayString(min, max, only = false, includeEndpoint = true) {
    max = max && max % 1 == 0 ? max.toInteger() : max
    min = min && min % 1 == 0 ? min.toInteger() : min

    String result

    if( min == null && max == null && only ) {
        // No splitpoints
        result = "any value"
    } else if( max == null && includeEndpoint ) {
        result = "greater than ${min ?: "something"}"
    } else if( min == null ) {
        result = "less than"
        if( includeEndpoint ) {
            result += " ${max ?: "something"}"
        }
    } else {
        result = "from ${min ?: "one thing"} to"
        if( includeEndpoint ) {
            result += " ${max ?: "another"}"
        }
    }
    return result
}

void appButtonHandler(String button) {
    debug "Button pressed: ${button}"
    def components = button.split("_")
    if( components.size() != 3 ) {
        log.warn "Invalid button name ${button}"
        return
    }
    def action = components[0]
    if( !["Delete", "Divide"].contains(action) ) {
        log.warn "Invalid action ${action} in button name ${button}"
        return
    }

    def devicePrefix = components[1]
    if( !PREFIXES.contains(devicePrefix) ) {
        log.warn "Invalid device prefix ${devicePrefix} in button name ${button}"
        return
    }

    def index = components[2].toInteger()

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
        def oldSplitpointCount = state["${devicePrefix}SplitpointCount"] ?: 0
        state["${devicePrefix}SplitpointCount"] = oldSplitpointCount + 1;
    }

    def firstValues = getValues("first")*.keySlug
    def secondValues = getValues("second")*.keySlug

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
    PREFIXES.each { currentPrefix ->
        def currentValues = deviceContext[currentPrefix].values
        def currentRange

        if (devicePrefix == currentPrefix) { // This is the device being affected by the action
            if (action == "Divide") {
                // Divide: Shift elements from 'index' onwards UP by 1.
                // Source: i, Dest: i+1
                // Iterate from last element down to index.
                currentRange = (currentValues.size() - 2)..index // Inclusive range
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
        (index..<state["${devicePrefix}SplitpointCount"]).each { i ->
            def oldKey = "${devicePrefix}_Splitpoint_${i + 1}"
            def newKey = "${devicePrefix}_Splitpoint_${i}"
            debug "Renaming ${oldKey} to ${newKey}"
            if( settings[oldKey] != null ) {
                app.updateSetting(newKey, settings[oldKey])
            }
            app.clearSetting(oldKey)
        }
        state["${devicePrefix}SplitpointCount"] = state["${devicePrefix}SplitpointCount"] - 1;
        cleanup()
    }
}

void updated() {
	unsubscribe()
    cleanup()
	initialize()
}

void cleanup() {
    def firstValues = getValues("first")*.keySlug
    def secondValues = getValues("second")*.keySlug
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

    PREFIXES.each { devicePrefix ->
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

        if( device && attribute ) {
            debug "Subscribing to ${device} ${attribute}"
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
        debug "Received ${property} ${value} from ${source} (${description ?: ""})"
        description = description ?: "${source} ${property} became ${value}"
    }

    def childDevice = getChildDevice();
    childDevice.parse(
        parent.getDeviceTypes().
        find { it.capability == outputCapability }.
        properties.
        collect { outputAttribute ->
            def output = childDevice.getSupportedAttributes().find { it.name == outputAttribute };
            def outputType = output.dataType;

            def keySlugs = PREFIXES.collect { devicePrefix ->
                def device = settings["${devicePrefix}Device"];
                def attributeName = settings["${devicePrefix}AttributeName"];
                def attributeType = device?.getSupportedAttributes()?.find { it.name == attributeName }?.dataType;
                def inputValue = device?.currentValue(attributeName);
                if( !device || !attributeName || !attributeType || inputValue == null ) {
                    debug "No device or attribute for ${devicePrefix} input"
                    return null;
                }

                def options = getValues(devicePrefix);
                def defaultKey = null;
                switch (attributeType) {
                    case "NUMBER":
                        // For NUMBER, we need to check ranges
                        def range = options.find {
                            (inputValue >= it.min || it.min == null) &&
                            (inputValue < it.max || it.max == null) };
                        if( range ) {
                            return range.keySlug;
                        }
                        log.warn "No match for ${devicePrefix} value ${inputValue}, using range ${options.inspect()}"
                        return null;
                    // For ENUM and STRING, we need to find the exact
                    // match; the only difference is that STRING has a
                    // default option.
                    case "STRING":
                        defaultKey = DEFAULT;
                        // Deliberate fallthrough
                    case "ENUM":
                        return options.find { it.exact == inputValue }?.keySlug ?: defaultKey;
                    default:
                        log.warn "Input attribute ${devicePrefix} has an unsupported type (${attributeType})"
                        return null;
                }
            };
            // Directly producing it here works for ENUM and simple STRINGs;
            // NUMBERs and complex STRINGs will require more intermediate
            // logic.
            def outputOption = getValue(outputAttribute, keySlugs.first(), keySlugs.last());
            if( !outputOption ) {
                // If no value is set, we use UNCHANGED
                outputOption = UNCHANGED;
            }
            else {
                if (["NUMBER", "STRING"].contains(outputType)) {
                    // For NUMBER and STRING, we need to process the value
                    outputOption = processString(outputOption ?: "", outputAttribute);
                }
                if( outputType == "NUMBER" ) {
                    if( outputOption == null || !outputOption.isNumber() ) {
                        log.warn "Output value for ${outputAttribute} is not a number: ${outputOption}"
                        outputOption = UNCHANGED;
                    }
                    else if( outputOption.isNumber() ) {
                        outputOption = outputOption.toBigDecimal();
                    }
                }
            }
            [
                name: outputAttribute,
                value: outputOption,
                descriptionText: description
            ]
        }.findAll{ it.value != UNCHANGED && it.value != null }
    );
}

private getValue(outputAttribute, firstValueKey, secondValueKey = null) {
    def key = constructKey(outputAttribute, firstValueKey, secondValueKey);
    return settings[key];
}

private constructKey(outputAttribute, firstValueKey, secondValueKey = null) {
    def key = "${outputAttribute}-${firstValueKey}"
    if( secondValueKey != null ) {
        key += "-${secondValueKey}"
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
@Field static final String DEFAULT = "__DEFAULT__"
@Field static final String[] PREFIXES = ["first", "second"]


// --- Shunting-Yard Algorithm Components (retained from previous interactions) ---

@Field static final Map operatorPrecedence = [
    '+': 1,
    '-': 1,
    '*': 2,
    '/': 2
]

def isOperator(token) {
    operatorPrecedence.containsKey(token)
}

def applyOperation(op, val1, val2) {
    switch (op) {
        case '+': return val1 + val2
        case '-': return val1 - val2
        case '*': return val1 * val2
        case '/':
            if (val2 == 0) throw new ArithmeticException("Division by zero")
            return val1 / val2
        default: throw new IllegalArgumentException("Unknown operator: $op")
    }
}

def evaluateMathExpression(List<String> tokens) {
    if (tokens.isEmpty()) return ''

    def outputQueue = []
    def operatorStack = []

    debug "Evaluating math expression with tokens: ${tokens.inspect()}"

    for (token in tokens) {
        if (token.isNumber()) { // Groovy's isNumber() handles decimals
            outputQueue.add(token as BigDecimal)
        } else if (isOperator(token)) {
            while (!operatorStack.isEmpty() && isOperator(operatorStack.last()) &&
                   operatorPrecedence[operatorStack.last()] >= operatorPrecedence[token]) {
                outputQueue.add(operatorStack.pop())
            }
            operatorStack.push(token)
        } else if (token == '(') {
            operatorStack.push(token)
        } else if (token == ')') {
            while (!operatorStack.isEmpty() && operatorStack.last() != '(') {
                outputQueue.add(operatorStack.pop())
            }
            if (!operatorStack.isEmpty() && operatorStack.last() == '(') {
                operatorStack.pop()
            } else {
                throw new IllegalArgumentException("Mismatched parentheses")
            }
        } else {
            throw new IllegalArgumentException("Unexpected token in math expression: $token")
        }
    }

    while (!operatorStack.isEmpty()) {
        if (operatorStack.last() == '(' || operatorStack.last() == ')') {
            throw new IllegalArgumentException("Mismatched parentheses")
        }
        outputQueue.add(operatorStack.pop())
    }

    def evaluationStack = []
    for (token in outputQueue) {
        if (token instanceof BigDecimal) {
            evaluationStack.push(token)
        } else {
            if (evaluationStack.size() < 2) {
                throw new IllegalArgumentException("Insufficient operands for operator: $token")
            }
            def val2 = evaluationStack.pop()
            def val1 = evaluationStack.pop()
            evaluationStack.push(applyOperation(token, val1, val2))
        }
    }

    if (evaluationStack.size() != 1) {
        throw new IllegalArgumentException("Invalid expression. Leftover operands or operators.")
    }
    debug "Final evaluation stack: ${evaluationStack.inspect()}"

    return evaluationStack.pop()
}


// --- Main Processing Function ---

def processString(String inputString, String outputAttribute) {
    debug "Processing input string: ${inputString} for output attribute: ${outputAttribute}"
    // Step 1: Variable Extraction and Substitution
    // Define a highly unlikely placeholder string
    def ESCAPED_PERCENT_PLACEHOLDER = "__ESCAPED_PERCENT_MARKER_UNIQUE_12345__"

    // Pass 1: Replace all escaped percents (\%) with a temporary placeholder
    def tempStringForVars = inputString.replaceAll(/\\%/, ESCAPED_PERCENT_PLACEHOLDER)

    def substitutedString = tempStringForVars.replaceAll(/%([^%]+)%/) { match, varName ->
        def trimmedVarName = varName.trim()
        def resolvedValue

        if (PREFIXES.contains(trimmedVarName)) {
            def device = settings["${trimmedVarName}Device"]
            def attributeName = settings["${trimmedVarName}AttributeName"]
            def inputValue = device?.currentValue(attributeName)
            resolvedValue = inputValue
        } else if (trimmedVarName == 'current') {
            def childDevice = getChildDevice()
            def inputValue = childDevice?.currentValue(outputAttribute)
            resolvedValue = inputValue
        } else {
            def globalVar = getGlobalVar(trimmedVarName)
            if( globalVar ) {
                resolvedValue = globalVar.value
            } else {
                // If it's not a global variable, don't substitute it.
                resolvedValue = match
            }
            resolvedValue = getGlobalVar(trimmedVarName)?.value
        }
        return resolvedValue != null ? resolvedValue.toString() : ''
    }

    // Pass 3: Restore the literal percent signs from the placeholders
    substitutedString = substitutedString.replaceAll(ESCAPED_PERCENT_PLACEHOLDER, "%")
    debug "Substituted string: ${substitutedString}"

    // Step 2: Extract and parse math segments using replaceAll
    def MATH_ATOM_PATTERN_TEXT = /(?:(?:(?:\d+(?:\.\d+)?)|(?:\.\d+))|[+\-*\/()])/
    def mathTokenFinder = ~ /(${MATH_ATOM_PATTERN_TEXT})/ // Used for tokenizing
    def mathExpressionPattern = ~/((${MATH_ATOM_PATTERN_TEXT}(?:\s*${MATH_ATOM_PATTERN_TEXT})*))/ // Main match

    def finalString = substitutedString.replaceAll(mathExpressionPattern) { allMatches ->
        def fullMatch = allMatches[0] // The entire matched segment
        debug "Value of 'fullMatch': '${fullMatch.inspect()}'" // Print value to see what it contains

        // If the entire matched segment is a simple number (positive, negative, or decimal),
        // return it directly. Groovy's `isNumber()` handles this effectively.
        if (fullMatch.isNumber()) {
            return fullMatch
        }

        // If it's not a simple number, proceed with tokenization and full evaluation
        def rawTokens = fullMatch.findAll(mathTokenFinder)
        debug "Raw tokens for evaluation: ${rawTokens.inspect()}"

        // This logic runs only for expressions that are NOT simple numbers,
        // e.g., "5 + -2", "(-5) * 2"
        def tokensForEvaluation = []
        boolean expectingValue = true

        rawTokens.each { token ->
            if (token.isNumber()) {
                tokensForEvaluation.add(token)
                expectingValue = false
            } else if (token == '(') {
                tokensForEvaluation.add(token)
                expectingValue = true
            } else if (token == ')') {
                tokensForEvaluation.add(token)
                expectingValue = false
            } else if (isOperator(token)) {
                if ((token == '-' || token == '+') && expectingValue) {
                    tokensForEvaluation.add("0")
                    tokensForEvaluation.add(token)
                    expectingValue = true
                } else if (!expectingValue) {
                    tokensForEvaluation.add(token)
                    expectingValue = true
                } else {
                    tokensForEvaluation.add(token)
                    expectingValue = true
                }
            } else {
                tokensForEvaluation.add(token)
                expectingValue = false
            }
        }
        debug "Tokens for evaluation: ${tokensForEvaluation.inspect()}"

        try {
            def result = evaluateMathExpression(tokensForEvaluation)
            debug "Math evaluation result: ${result}"
            return result.toString()
        } catch (e) {
            log.warn "Math evaluation failed for expression '$fullMatch'. Error: ${e.message}"
            return fullMatch
        }
    }

    return finalString.trim()
}
