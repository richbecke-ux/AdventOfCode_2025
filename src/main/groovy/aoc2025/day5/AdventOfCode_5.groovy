package main.groovy.aoc2025.day5

import groovy.transform.Field

def testData = '''\
3-5
10-14
16-20
12-18

1
5
8
11
17
32'''

class Range {
    long start, end

    Range (String spec){
        (start, end) = spec.split('-')*.toLong()
    }

    def isWithinRange(long number) {
        number >= start && number <= end
    }
}

@Field def ranges = []

def foundInRanges(long value) {
    for (int i = 0; i < ranges.size(); ++i)
    {
        if (ranges[i].isWithinRange(value)) {
            return true
        }
    }
    false
}

def numFresh = 0

def input = args ? new File(args[0]).text : testData
def lines = input.readLines().iterator()
while (lines.hasNext()) {
    def line = lines.next()
    if (line == "") {
        break
    }
    ranges << new Range (line)
}

while (lines.hasNext()) {
    def line = lines.next()
    def value = line as long
    if (foundInRanges(value)) {
        ++numFresh
    }
}

println "Number of fresh ingredients: $numFresh"
