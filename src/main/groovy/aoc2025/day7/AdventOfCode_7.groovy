package aoc2025.day7

class BeamSegment {
    static List inventory
    static int splits = 0
    int x, y
    BeamSegment leftSplit, rightSplit

    static void initialize(int numRows) {
        inventory = (0..<numRows).collect { [:] }
    }

    BeamSegment(List grid, int x, int y)
    {
        this.x = x
        this.y = y
        inventory[y][x] = this
        propagate (grid)
    }

    void propagate (ArrayList grid) {
        for (i in y..<grid.size()) {
            if (grid[i][x] == '^' as char) {
                def isNewSplit = false
                if (x > 0) {
                    if (!inventory[i][x - 1]) {
                        leftSplit = new BeamSegment(grid, x - 1, i)
                        isNewSplit = true
                    } else {
                        leftSplit = inventory[i][x - 1]
                    }
                }
                if (x < (grid[y].length() - 1)) {
                    if (!inventory[i][x + 1]) {
                        rightSplit = new BeamSegment(grid, x + 1, i)
                        isNewSplit = true
                    } else {
                        rightSplit = inventory[i][x + 1]
                    }
                }
                if (isNewSplit) ++splits
                break
            }
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
def testResult2 = 0

def lines = (args ? new File(args[0]).text : testData).readLines()
BeamSegment.initialize(lines.size())

int startX = lines[0].indexOf("S")
BeamSegment beamStart = new BeamSegment (lines, startX, 0)

println "Beam splits: $BeamSegment.splits"