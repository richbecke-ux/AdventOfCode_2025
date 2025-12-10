package aoc2025.day7

class BeamSegment {
    static List<Map<Integer, BeamSegment>> inventory
    static int splits = 0
    int x, y
    BeamSegment leftSplit, rightSplit
    BigInteger cachedCount

    static void initialize(int numRows) {
        inventory = (0..<numRows).collect { [:] }
    }

    BeamSegment(List<String> grid, int x, int y) {
        this.x = x
        this.y = y
        inventory[y][x] = this
        propagate(grid)
    }

    void propagate(List<String> grid) {
        for (i in y..<grid.size()) {
            if (grid[i][x] != '^' as char) continue

            def isNew = [[-1, { leftSplit = it }], [1, { rightSplit = it }]].collect { delta, assign ->
                def newX = x + delta
                if (newX < 0 || newX >= grid[0].size()) return false
                def existed = inventory[i][newX] as boolean
                assign(inventory[i][newX] ?: new BeamSegment(grid, newX, i))
                !existed
            }
            if (isNew.any()) ++splits
            break
        }
    }

    BigInteger traverse() {
        if (cachedCount != null) return cachedCount
        cachedCount = (leftSplit == null && rightSplit == null) ? 1G :
                (leftSplit?.traverse() ?: 0G) + (rightSplit?.traverse() ?: 0G)
    }
}

def testData = '''\
.......S.......
...............
.......^.......
...............
......^.^......
...............
.....^.^.^.....
...............
....^.^...^....
...............
...^.^...^.^...
...............
..^...^.....^..
...............
.^.^.^.^.^...^.
...............'''

def testResult1 = 21
def testResult2 = 40

def lines = (args ? new File(args[0]).text : testData).readLines()
BeamSegment.initialize(lines.size())

def beamStart = new BeamSegment(lines, lines[0].indexOf('S'), 0)

if (!args) assert BeamSegment.splits == testResult1
println "Beam splits: ${BeamSegment.splits}"

def timeLines = beamStart.traverse()

if (!args) assert timeLines == testResult2
println "Timelines: $timeLines"