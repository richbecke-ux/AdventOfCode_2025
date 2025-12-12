package aoc2025.day9

def testData = '''\
7,1
11,1
11,7
9,7
9,5
2,5
2,3
7,3'''

def coords = (args ? new File(args[0]).text : testData)
        .readLines()
        .collect { it.split(',')*.toLong() }

println coords.withIndex().collectMany { a, i ->
    coords.drop(i + 1).collect { b ->
        ((a[0] - b[0]).abs() + 1) * ((a[1] - b[1]).abs() + 1)
    }
}.max()