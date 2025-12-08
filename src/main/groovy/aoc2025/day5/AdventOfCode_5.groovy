package main.groovy.aoc2025.day5

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

    Range(String spec) {
        (start, end) = spec.split('-')*.toLong()
    }

    long span() { end - start + 1 }

    boolean isWithinRange(long number) { number >= start && number <= end }

    boolean isOverlapping(Range other) { other.start <= end + 1 && other.end >= start - 1 }

    void consolidate(Range other) {
        start = Math.min(start, other.start)
        end = Math.max(end, other.end)
    }

    String toString() { "$start-$end" }
}

def input = args ? new File(args[0]).text : testData
def sections = input.split(/\n\n/)

def ranges = sections[0].readLines().collect { new Range(it) }
def values = sections[1].readLines()*.toLong()

def numFresh = values.count { v -> ranges.any { r -> r.isWithinRange(v) } }
println "Number of fresh ingredients: $numFresh"

ranges.sort { it.start }
def consolidated = ranges.drop(1).inject([ranges[0]]) { acc, r ->
    if (acc[-1].isOverlapping(r)) {
        acc[-1].consolidate(r)
    } else {
        acc << r
    }
    acc
}

println "Total number of IDs in fresh ingredient ranges: " + consolidated.sum { it.span() }

/*
// "Problemløsningsversjon" - få mer eksplisitt tradisjonell Java-style kode til å funke før refaktorering til idiomatisk Groovy med Collections & closures
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

    def span () {
        end - start + 1
    }

    def isWithinRange(long number) {
        number >= start && number <= end
    }

    def isOverLapping(Range range) {
        range.start <= this.end + 1 && range.end >= this.start - 1
    }

    def consolidate(Range range){
        start = range.start < this.start ? range.start : this.start
        end = range.end > this.end ? range.end : this.end
    }

    String toString() {
        start as String + "-" + end as String
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

int removed, passes = 0
do {
    removed = 0
    for (int i = 0; i < ranges.size() - 1; ++i) {
        for (int j = i + 1; j < ranges.size(); ++j) {
            if (ranges[i].isOverLapping(ranges[j])) {
                print "Overlap: $i " + ranges[i] + " and $j " + ranges[j]
                ranges[i].consolidate(ranges[j])
                println" - consolideted to $i " + ranges[i]
                ranges.remove(j)
                ++removed
                j = i
            }
        }
    }
    ++passes
} while (removed)

println "Passes to consolidate ranges: $passes"
println "Total number of IDs in fresh ingredient ranges: " + ranges.sum {it.span()}
*/
