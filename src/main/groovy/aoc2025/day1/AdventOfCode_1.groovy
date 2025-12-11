package aoc2025.day1

class LockDial {
    int size,
        position,
        zeroPasses,
        zeroStops

    void rotate(String spec){
        int direction = spec.charAt(0) == 'L' ? -1 : 1
        int amount = spec.substring(1) as Integer
        int distanceToZero = position == 0 ? 100 : direction == -1 ? position : size - position
        zeroPasses += amount.intdiv(size)
        if ((amount % size) >= distanceToZero){
            ++zeroPasses
        }
        position = (position + direction * amount) % size
        position += (position < 0 ? size : 0)
        if (position == 0){
            ++zeroStops
        }
    }
}

String testInput = '''L68
L30
R48
L5
R60
L55
L1
L99
R14
L82'''

LockDial dial = new LockDial (size: 100, position: 50)

new File(args[0]).eachLine { String line ->
    //   new StringReader (testInput).eachLine { String line ->
    if ("RL".contains (line.charAt(0) as String)) {
        dial.rotate (line)
    }
}

println "Stops at zero position: $dial.zeroStops, total number of times at zero position: $dial.zeroPasses"
