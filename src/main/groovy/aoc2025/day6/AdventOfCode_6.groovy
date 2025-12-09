package aoc2025.day6

List.metaClass.product = { -> delegate.inject(1L) { acc, val -> acc * val } }

def testData = '''\
123 328  51 64
 45 64  387 23
  6 98  215 314
*   +   *   +  '''
def testSum = 4277556

def lines = (args ? new File(args[0]).text : testData).readLines()
def columns = lines.dropRight(1).collect { it.trim().split(/\s+/)*.toLong() }.transpose()
def operators = lines[-1].split(/\s+/).collect { it as char }

assert operators.size() == columns.size() && columns*.size().unique().size() == 1

def sum = [columns, operators].transpose().sum { col, op ->
    op == '+' ? col.sum() : col.product()
}

if (!args) assert sum == testSum

println "Sum: $sum"