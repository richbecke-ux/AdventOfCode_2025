package aoc2025.day7

class BeamSegment {
    static List<Map<Integer, BeamSegment>> inventory
    static int splits = 0
    int x, y
    BeamSegment leftSplit, rightSplit

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

            def isNewSplit = false

            if (x > 0 && !inventory[i][x - 1]) {
                leftSplit = new BeamSegment(grid, x - 1, i)
                isNewSplit = true
            } else if (x > 0) {
                leftSplit = inventory[i][x - 1]
            }

            if (x < grid[0].size() - 1 && !inventory[i][x + 1]) {
                rightSplit = new BeamSegment(grid, x + 1, i)
                isNewSplit = true
            } else if (x < grid[0].size() - 1) {
                rightSplit = inventory[i][x + 1]
            }

            if (isNewSplit) splits++
            break
        }
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

def lines = (args ? new File(args[0]).text : testData).readLines()
BeamSegment.initialize(lines.size())

def startX = lines[0].indexOf('S')
new BeamSegment(lines, startX, 0)

if (!args) assert BeamSegment.splits == testResult1

println "Beam splits: ${BeamSegment.splits}"