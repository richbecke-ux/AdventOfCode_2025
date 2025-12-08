package aoc2025

def testData = '''\
..@@.@@@@.
@@@.@.@.@@
@@@@@.@.@@
@.@@@@..@.
@@.@@@@.@@
.@@@@@@@.@
.@.@.@.@@@
@.@@@.@@@@
.@@@@@@@@.
@.@.@@@.@.'''

def input = args ? new File(args[0]).text : testData

def floorMap = input.readLines()*.toCharArray()
int height = floorMap.size()
int width = floorMap[0].size()

def countNeighbourRolls = { int y, int x ->
    (-1..1).sum { dy ->
        (-1..1).count { dx ->
            def ny = y + dy, nx = x + dx
            (dy || dx) && ny in 0..<height && nx in 0..<width && floorMap[ny][nx] == '@'
        }
    }
}

def isRemovable = { int y, int x -> floorMap[y][x] == '@' && countNeighbourRolls(y, x) < 4 }

println "Part 1 movable rolls: ${(0..<height).sum { y -> (0..<width).count { x -> isRemovable(y, x) } }}"

long total = 0
while (true) {
    int removed = 0
    for (y in 0..<height) {
        for (x in 0..<width) {
            if (isRemovable(y, x)) {
                floorMap[y][x] = '.' as char
                ++removed
            }
        }
    }
    if (!removed) break
    total += removed
}

println "Part 2 movable rolls: $total"

/*
// "Problemløsningsversjon" - få mer eksplisitt tradisjonell Java-style kode til å funke før refaktorering til idiomatisk Groovy med Collections & closures
import groovy.transform.Field

@Field def floorMap = []
@Field int width, height

def isRollInPos (int y, int x) {
    if (x < 0 || x >= width || y < 0 || y >= height) {
        0
    } else {
        floorMap[y][x] == "@" ? 1 : 0
    }
}

def countNeighborRolls(int y, int x) {
    def count = 0
    for (int i = x - 1; i <= x + 1; ++i) {
        for (int j = y - 1; j <= y + 1; ++j) {
            if ((i != x) || (j != y)) {
                count += isRollInPos(j, i)
            }
        }
    }
    return count
}

testData = '''\
..@@.@@@@.
@@@.@.@.@@
@@@@@.@.@@
@.@@@@..@.
@@.@@@@.@@
.@@@@@@@.@
.@.@.@.@@@
@.@@@.@@@@
.@@@@@@@@.
@.@.@@@.@.'''

//def input = testData
def input = new File(args[0]).text

input.eachLine { String line ->
    floorMap << line.toCharArray()
}

width = floorMap[0].size()
height = floorMap.size()
def total = 0

for (int y = 0; y < height; ++y) {
    for (int x = 0; x < width; ++x) {
        if (floorMap[y][x] == "@" && countNeighborRolls(y, x) < 4) {
            ++total
        }
    }
}

println "Part 1 movable rolls: $total"

total = 0
def moved
do {
    moved = 0
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            if (floorMap[y][x] == "@" && countNeighborRolls(y, x) < 4) {
                floorMap[y][x] = "."
                ++moved
            }
        }
    }
    total += moved
} while (moved > 0)

println "Part 2 movable rolls: $total"
*/