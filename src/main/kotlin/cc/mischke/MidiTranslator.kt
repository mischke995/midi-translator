package cc.mischke

import java.io.File
import javax.sound.midi.*

fun main(args: Array<String>) {
    val midiDevices: List<MidiDevice> = MidiSystem.getMidiDeviceInfo().map {
        MidiSystem.getMidiDevice(it)
    }
    midiDevices.forEach {
        println(it.deviceInfo.description)
    }

    val midiInDevices: List<MidiDevice> = midiDevices.filter { it.maxTransmitters != 0 }
    val midiOutDevices: List<MidiDevice> = midiDevices.filter { it.maxReceivers != 0 }

    if (midiInDevices.isEmpty()) {
        println("No MIDI input detected!")
        return
    }
    if (midiOutDevices.isEmpty()) {
        println("No MIDI output detected!")
        return
    }

    println("\nSelect MIDI input")
    val midiInDevice: MidiDevice = selectMidiDevice(midiInDevices)

    println("\nSelect MIDI output")
    val midiOutDevice: MidiDevice = selectMidiDevice(midiOutDevices)

    val translator = MidiTranslator(
        args.getOrNull(0),
        midiInDevice,
        midiOutDevice
    )

    translator.start()
}

fun selectMidiDevice(midiDevices: List<MidiDevice>): MidiDevice {
    var index = 0
    midiDevices.forEach {
        println("${++index}:\t${it.deviceInfo.name}")
        println("\t${it.deviceInfo.description}")
        println("\t${it.deviceInfo.vendor}")
        println("\t${it.deviceInfo.version}")
        println()
    }
    var selectedDevice = 0

    while (selectedDevice <= 0 || selectedDevice > index) {
        print("Select [1-${index}]: ")
        selectedDevice = readLine()?.toIntOrNull() ?: index
        if (selectedDevice <= 0 || selectedDevice > index)
            println("Invalid input, try again!")
    }

    return midiDevices[selectedDevice - 1]

}

class MidiTranslator(
    mappingFilePath: String?,
    private val midiInDevice: MidiDevice,
    private val midiOutDevice: MidiDevice
) : Receiver {
    private val mappings: Map<String, MidiMessage>

    init {
        this.mappings = generateMidiMapping(mappingFilePath)
    }

    private fun generateMidiMapping(mappingFilePath: String?): Map<String, MidiMessage> {
        val mappings = mutableMapOf<String, MidiMessage>()

        mappingFilePath ?: return mappings
        val mappingFile = File(mappingFilePath)
        if (!mappingFile.isFile) {
            println("WARNING: There is something wrong with the provided mapping file $mappingFilePath")
            return mappings
        }

        mappingFile.forEachLine { it ->
            val mapping = it.split("|")
            if (mapping.size != 2) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }

            val input = mapping.first().split(",").map { input -> input.toIntOrNull(16) }
            val output = mapping.last().split(",").map { output -> output.toIntOrNull(16) }
            if (input.size != 3 || output.size != 3) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }

            if (input[0] != null && !(input[0] in (0x80..0xEF) || input[0] == 0xF2)) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }
            if (input[1] != null && input[1] !in (0x00..0x7F)) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }
            if (input[2] != null && input[2] !in (0x00..0x7F)) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }
            if (output[0] != null && !(output[0] in (0x80..0xEF) || output[0] == 0xF2)) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }
            if (output[1] != null && output[1] !in (0x00..0x7F)) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }
            if (output[2] != null && output[2] !in (0x00..0x7F)) {
                println("WARNING: Can not create mapping - \"$it\"")
                return@forEachLine
            }

            for (val1 in (input[0] ?: 0x80)..(input[0] ?: 0xEF).and(input[0] ?: 0xF2)) {
                for (val2 in (input[1] ?: 0x00)..(input[1] ?: 0x7F)) {
                    for (val3 in (input[2] ?: 0x00)..(input[2] ?: 0x7F)) {
                        mappings["$val1,$val2,$val3"] ?: run {
                            mappings["$val1,$val2,$val3"] = ShortMessage(
                                output[0] ?: val1,
                                output[1] ?: val2,
                                output[2] ?: val3
                            )
                        }
                    }
                }
            }
        }

        return mappings
    }

    fun start() {
        val sourceTransmitter = midiInDevice.transmitter
        sourceTransmitter.receiver = this
        midiInDevice.open()
        println(" successfully connect to MIDI IN")

        midiOutDevice.open()
        println(" successfully connect to MIDI OUT")
    }

    override fun send(message: MidiMessage?, timeStamp: Long) {
        if (message != null && message is ShortMessage && message.length == 3) {
            val out = mappings["${message.command},${message.data1},${message.data2}"] as ShortMessage? ?: message


            println("%02x %02x %02x -> %02x %02x %02x".format(message.command,message.data1,message.data2,out.command,out.data1,out.data2))

            midiOutDevice.receiver.send(out, timeStamp)
        } else {
            midiOutDevice.receiver.send(message, timeStamp)
        }
    }

    override fun close() {}
}
