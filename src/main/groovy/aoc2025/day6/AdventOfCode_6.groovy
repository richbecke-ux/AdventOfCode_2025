package aoc2025.day6

List.metaClass.product = { -> delegate.inject(1L) { acc, val -> acc * val } }

def testData = '''\
123 328  51 64
 45 64  387 23
  6 98  215 314
*   +   *   +  '''
def testSum1 = 4277556
def testSum2 = 3263827

def lines = (args ? new File(args[0]).text : testData).readLines()
def columns = lines.dropRight(1).collect { it.trim().split(/\s+/)*.toLong() }.transpose()
def operators = lines[-1].split(/\s+/).collect { it as char }

assert operators.size() == columns.size() && columns*.size().unique().size() == 1

def sum = [columns, operators].transpose().sum { col, op ->
    op == '+' ? col.sum() : col.product()
}

if (!args) assert sum == testSum1

println "Part 1 sum: $sum"

lines = (args ? new File(args[0]).text : testData).readLines()
def numberLines = lines.dropRight(1)
def columnStart = lines[-1].findIndexValues { it != ' ' }*.intValue()

columns = numberLines.collect { line ->
    columnStart.indexed().collect { i, start ->
        def end = (i < columnStart.size() - 1) ? columnStart[i + 1] - 1 : line.size()
        (start < line.size()) ? line[start..<Math.min(end, line.size())] : ''
    }
}.transpose().with { cols ->
    cols[-1] = cols[-1].collect { it.padRight(cols[-1]*.size().max()) }
    cols
}

sum = columns.indexed().collect { i, column ->
    def numbers = (0..<column[0].size()).collect { j ->
        column.collect { it[j] }.join().trim().toLong()
    }
    operators[i] == '+' ? numbers.sum() : numbers.product()
}.sum()

if (!args) assert sum == testSum2

println "Part 2 sum: $sum"