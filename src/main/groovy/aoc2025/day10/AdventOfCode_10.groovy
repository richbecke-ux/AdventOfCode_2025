package aoc2025.day10

def testData = '''\
[.##.] (3) (1,3) (2) (2,3) (0,2) (0,1) {3,5,4,7}
[...#.] (0,2,3,4) (2,3) (0,4) (0,1,2) (1,2,3,4) {7,5,12,7,2}
[.###.#] (0,1,2,3,4) (0,3,4) (0,1,2,4,5) (1,2) {10,11,11,5,10,5}'''


class Node {
    int state
    List<Integer> remainingMasks
    List<Integer> path = []
    int level

    Node(int state, List<Integer> remainingMasks, List<Integer> path, int level) {
        this.state = state
        this.remainingMasks = remainingMasks
        this.path = path
        this.level = level
    }
}

// Converts a diagram string to an integer mask
int diagramToBits(String diagram) {
    int bits = 0
    diagram.eachWithIndex { symbol, i ->
        if (symbol == '#') bits |= (1 << i)
    }
    return bits
}

String bitsToDiagram(int bits, int length) {
    return (0..<length).collect { i -> (bits & (1 << i)) ? '#' : '.' }.join('')
}


def solve(int targetState, List<Integer> bitMaskList, int diagLen) {

    Node root = new Node(0, bitMaskList, [], 0)

    List<List<Node>> levels = []
    List<Node> level1 = []

    for (int i = 0; i < root.remainingMasks.size(); i++) {
        int mask = root.remainingMasks[i]
        if ((mask & targetState) != 0) {
            int nextState = root.state ^ mask
            def nextRemaining = []
            root.remainingMasks.eachWithIndex { m, idx -> if(idx != i) nextRemaining << m }

            Node child = new Node(nextState, nextRemaining, [mask], 1)

            if (nextState == targetState) return child
            level1 << child
        }
    }

    levels << level1
    int currentLevelIdx = 0

    while (currentLevelIdx < levels.size()) {
        List<Node> currentLevelNodes = levels[currentLevelIdx]
        List<Node> nextLevel = []

        for (Node parent : currentLevelNodes) {
            for (int i = 0; i < parent.remainingMasks.size(); i++) {
                int mask = parent.remainingMasks[i]
                int nextState = parent.state ^ mask

                def nextRemaining = []
                parent.remainingMasks.eachWithIndex { m, idx -> if(idx != i) nextRemaining << m }

                Node child = new Node(nextState, nextRemaining, parent.path + mask, parent.level + 1)

                if (nextState == targetState) return child

                nextLevel << child
            }
        }
        levels << nextLevel
        currentLevelIdx++
    }
    return null
}

int buttonPresses = 0
def initTime = System.currentTimeMillis()

(args ? new File(args[0]).text : testData).eachLine { line ->

    println "\n$line"

    def diagramMatch = (line =~ /\[([^\]]+)\]/)
    String rawDiagram = diagramMatch ? diagramMatch[0][1] : ""
    int diagramMask = diagramToBits(rawDiagram)

    def parenMatches = (line =~ /\(([^)]+)\)/)
    List<Integer> bitmaskList = parenMatches.collect { match ->
        match[1].split(',').collect { it.trim().toInteger() }.inject(0) { acc, pos -> acc | (1 << pos) }
    }

    def braceMatch = (line =~ /\{([^}]+)\}/)
    List<Integer> valuesList = braceMatch ? braceMatch[0][1].split(',').collect { it.trim().toInteger() } : []

    def startTime = System.currentTimeMillis()

    Node result = solve(diagramMask, bitmaskList, rawDiagram.length())

    def elapsedTime = System.currentTimeMillis() - startTime

    if (result) {
        println " - target reached in $elapsedTime ms at depth ${result.level}."
        println " - sequence of masks: ${result.path}"
    }
    else {
        println (" - no solution found ($elapsedTime ms elapsed) with given button masks")
    }

    buttonPresses += result.level
}

println "\nTotal number of button presses: $buttonPresses (${System.currentTimeMillis() - initTime} ms elapsed)"