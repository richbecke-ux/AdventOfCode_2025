package aoc2025.day8

List.metaClass.product = { -> delegate.inject(1L) { acc, val -> acc * val } }

def testData = '''\
162,817,812
57,618,57
906,360,560
592,479,940
352,342,300
466,668,158
542,29,236
431,825,988
739,650,466
52,470,668
216,146,977
819,987,18
117,168,530
805,96,715
346,949,466
970,615,88
941,993,340
862,61,35
984,92,344
425,690,689'''

def testParam1= 10
def testResult1 = 40
def testResult2 = 0

def distance = { a, b ->
    Math.sqrt([a, b].transpose().sum { ((it[0] - it[1]) as Long)  ** 2 })
}

def idx = (args ? new File(args[0]).text : testData).readLines().collectEntries {
    def c = it.split(',')*.toInteger()
    [c, [c]]
}

def clCnt = idx.size()

def addPair = { a, b ->
    idx[a] != idx[b] && idx[a].addAll(idx[b]).with { --clCnt; idx[b].each { idx[it] = idx[a] } }
}

def dsts = (idx.keySet() as List).indexed().collectMany { i, a ->
    (idx.keySet() as List).drop(i + 1).collect { b -> [distance(a, b), a, b] }
}

dsts.sort {it[0]}

numDst = args ? 1000 : testParam1

dsts.take(numDst).each {
    addPair (it[1], it[2])
}

println "Part 1 answer: " + idx.values().unique(false).sort { -it.size() }.take(3)*.size().product()

def lastMerge
dsts.each {
    if (clCnt == 1) return
    if (addPair(it[1], it[2])) {
        lastMerge = [it[1], it[2]]
    }
}

println "Part 2 answer: " + ((lastMerge[0][0] as long) * lastMerge[1][0])