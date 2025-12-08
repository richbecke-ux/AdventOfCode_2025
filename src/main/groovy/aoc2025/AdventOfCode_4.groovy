package aoc2025

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
