package aoc2025.day9.claude

import java.util.concurrent.*

/**
 * Advent of Code 2025 - Dag 9: Movie Theater
 *
 * Fleksibel løsning med kommandolinjealternativer.
 *
 * Bruk:
 *   groovy day9.groovy [opsjoner]
 *
 * Opsjoner:
 *   -f <fil>    Les input fra fil (ellers brukes testdata)
 *   -p          Aktiver parallellisering
 *   -r          Reverser x/y-koordinater i input
 *   -h          Vis hjelp
 *
 * Eksempler:
 *   groovy day9.groovy -f input.txt
 *   groovy day9.groovy -f input.txt -p
 *   groovy day9.groovy -f input.txt -p -r
 *   groovy day9.groovy -r -p -f input.txt
 */

// ============================================================
// Testdata og forventede resultater
// ============================================================

def testData = '''\
7,1
11,1
11,7
9,7
9,5
2,5
2,3
7,3'''

def testResult1 = 50
def testResult2 = 24

// ============================================================
// Kommandolinjeparsing
// ============================================================

def parseArgs = { String[] arguments ->
    def config = [
            inputFile: null,
            parallel: false,
            reverse: false,
            help: false
    ]

    def i = 0
    while (i < arguments.length) {
        switch (arguments[i]) {
            case '-f':
                if (i + 1 < arguments.length) {
                    config.inputFile = arguments[i + 1]
                    i += 2
                } else {
                    println "FEIL: -f krever et filnavn"
                    System.exit(1)
                }
                break
            case '-p':
                config.parallel = true
                i++
                break
            case '-r':
                config.reverse = true
                i++
                break
            case '-h':
            case '--help':
                config.help = true
                i++
                break
            default:
                // Bakoverkompatibilitet: hvis første argument ikke starter med -,
                // behandle det som filnavn
                if (!arguments[i].startsWith('-') && config.inputFile == null) {
                    config.inputFile = arguments[i]
                    i++
                } else {
                    println "ADVARSEL: Ukjent opsjon '${arguments[i]}' ignorert"
                    i++
                }
        }
    }

    config
}

def showHelp = {
    println """
Advent of Code 2025 - Dag 9: Movie Theater

Bruk: groovy day9.groovy [opsjoner]

Opsjoner:
  -f <fil>    Les input fra fil (ellers brukes innebygd testdata)
  -p          Aktiver parallellisering (lønner seg for >1000 hjørner)
  -r          Reverser x/y-koordinater i input
  -h          Vis denne hjelpeteksten

Eksempler:
  groovy day9.groovy                      # Kjør med testdata
  groovy day9.groovy -f input.txt         # Les fra fil
  groovy day9.groovy -f input.txt -p      # Med parallellisering
  groovy day9.groovy -p -r -f input.txt   # Alle opsjoner (vilkårlig rekkefølge)
"""
}

def config = parseArgs(args)

if (config.help) {
    showHelp()
    System.exit(0)
}

// ============================================================
// Innlesning og parsing
// ============================================================

def totalStartTime = System.currentTimeMillis()

def inputText = config.inputFile ? new File(config.inputFile).text : testData
def useTestData = (config.inputFile == null)

def vertices = inputText
        .readLines()
        .collect { line ->
            def coords = line.split(',')*.toLong()
            config.reverse ? coords.reverse() : coords
        }

def (xs, ys) = vertices.transpose()
int n = vertices.size()

println "=" * 50
println "Konfigurasjon:"
println "  Input: ${config.inputFile ?: 'testdata'}"
println "  Parallellisering: ${config.parallel ? 'PÅ' : 'AV'}"
println "  Reverser koordinater: ${config.reverse ? 'JA' : 'NEI'}"
println "  Antall hjørner: $n"
if (config.parallel) {
    println "  Tilgjengelige tråder: ${Runtime.runtime.availableProcessors()}"
}
println "=" * 50

// ============================================================
// Sett opp thread pool hvis parallellisering er aktivert
// ============================================================

def pool = config.parallel ?
        Executors.newFixedThreadPool(Runtime.runtime.availableProcessors()) :
        null

// ============================================================
// Hjelpefunksjoner
// ============================================================

def rectArea = { a, b ->
    ((a[0] - b[0]).abs() + 1) * ((a[1] - b[1]).abs() + 1)
}

// ============================================================
// DEL 1: Største rektangel mellom vilkårlige hjørner
// ============================================================

def t1 = System.currentTimeMillis()

def area1
if (config.parallel) {
    // Parallell versjon: del opp etter første indeks
    def tasks = (0..<n).collect { i ->
        { ->
            long maxArea = 0
            for (int j = i + 1; j < n; j++) {
                long area = rectArea(vertices[i], vertices[j])
                if (area > maxArea) maxArea = area
            }
            maxArea
        } as Callable<Long>
    }
    area1 = pool.invokeAll(tasks).collect { it.get() }.max()
} else {
    // Sekvensiell versjon
    area1 = vertices.withIndex().collectMany { a, i ->
        vertices.drop(i + 1).collect { b -> rectArea(a, b) }
    }.max()
}

println "\nDel 1: $area1 (${System.currentTimeMillis() - t1}ms)"

// ============================================================
// DEL 2: Integral image tilnærming
// ============================================================

def t2 = System.currentTimeMillis()

// --- Steg 1: Koordinatkompresjon ---
def uniqueX = xs.unique().sort()
def uniqueY = ys.unique().sort()

def xMap = uniqueX.withIndex().collectEntries { val, idx -> [(val): idx] }
def yMap = uniqueY.withIndex().collectEntries { val, idx -> [(val): idx] }

int W = uniqueX.size()
int H = uniqueY.size()

// --- Steg 2: Finn vertikale kanter ---
def vEdges = (0..<n).findResults { i ->
    def v1 = vertices[i]
    def v2 = vertices[(i + 1) % n]

    if (v1[0] == v2[0]) {
        int gx = xMap[v1[0]]
        int gy1 = yMap[v1[1]]
        int gy2 = yMap[v2[1]]
        [gx, Math.min(gy1, gy2), Math.max(gy1, gy2)]
    } else {
        null
    }
}

// --- Steg 3: Scanline-rasterisering + prefix-sum ---

int[][] gridSum = new int[W][H]

if (config.parallel) {
    // Parallell versjon: beregn rad-verdier parallelt, deretter sekvensiell vertikal sum

    // Fase A: Parallell beregning av horisontal prefix-sum per rad
    def rowTasks = (0..<H).collect { y ->
        { ->
            def activeEdges = vEdges
                    .findAll { it[1] <= y && it[2] > y }
                    .sort { it[0] }

            int[] rowPrefixSum = new int[W]
            int runningSum = 0
            int edgeIdx = 0
            boolean inside = false

            for (int x = 0; x < W; x++) {
                while (edgeIdx < activeEdges.size() && activeEdges[edgeIdx][0] == x) {
                    inside = !inside
                    edgeIdx++
                }
                if (inside) runningSum++
                rowPrefixSum[x] = runningSum
            }

            [(y): rowPrefixSum]
        } as Callable
    }

    def rowResults = pool.invokeAll(rowTasks).collect { it.get() }

    // Samle resultater
    int[][] hPrefixSum = new int[W][H]
    rowResults.each { map ->
        map.each { y, rowData ->
            for (int x = 0; x < W; x++) {
                hPrefixSum[x][y] = rowData[x]
            }
        }
    }

    // Fase B: Sekvensiell vertikal prefix-sum
    for (int x = 0; x < W; x++) {
        int colSum = 0
        for (int y = 0; y < H; y++) {
            colSum += hPrefixSum[x][y]
            gridSum[x][y] = colSum
        }
    }
} else {
    // Sekvensiell versjon: alt i én pass
    for (int y = 0; y < H; y++) {
        def activeEdges = vEdges
                .findAll { it[1] <= y && it[2] > y }
                .sort { it[0] }

        int[] rowValues = new int[W]
        for (int k = 0; k < activeEdges.size() - 1; k += 2) {
            int xStart = activeEdges[k][0]
            int xEnd = activeEdges[k + 1][0]
            for (int x = xStart; x < xEnd; x++) {
                rowValues[x] = 1
            }
        }

        for (int x = 0; x < W; x++) {
            int val = rowValues[x]
            int left = (x > 0) ? gridSum[x - 1][y] : 0
            int top = (y > 0) ? gridSum[x][y - 1] : 0
            int diag = (x > 0 && y > 0) ? gridSum[x - 1][y - 1] : 0
            gridSum[x][y] = val + left + top - diag
        }
    }
}

def setupTime = System.currentTimeMillis() - t2
println "Grid setup: ${setupTime}ms (${W}×${H} = ${W*H} celler)"

// --- Steg 4: O(1) rektangel-oppslag ---
def getGridSum = { int x1, int y1, int x2, int y2 ->
    int minX = Math.min(x1, x2)
    int maxX = Math.max(x1, x2)
    int minY = Math.min(y1, y2)
    int maxY = Math.max(y1, y2)

    int qx1 = minX
    int qy1 = minY
    int qx2 = maxX - 1
    int qy2 = maxY - 1

    if (qx2 < qx1 || qy2 < qy1) return 0

    int total = gridSum[qx2][qy2]
    int removeLeft = (qx1 > 0) ? gridSum[qx1 - 1][qy2] : 0
    int removeTop = (qy1 > 0) ? gridSum[qx2][qy1 - 1] : 0
    int addDiag = (qx1 > 0 && qy1 > 0) ? gridSum[qx1 - 1][qy1 - 1] : 0

    total - removeLeft - removeTop + addDiag
}

// --- Steg 5: Søk gjennom hjørnepar ---

def searchStart = System.currentTimeMillis()

long area2 = 0L
def bestCorners = [null, null]

if (config.parallel) {
    // Parallell versjon
    def searchTasks = (0..<n).collect { i ->
        { ->
            def v1 = vertices[i]
            int ix1 = xMap[v1[0]]
            int iy1 = yMap[v1[1]]

            long localBest = 0
            int localBestJ = -1

            for (int j = i + 1; j < n; j++) {
                def v2 = vertices[j]
                int ix2 = xMap[v2[0]]
                int iy2 = yMap[v2[1]]

                int cellsWide = Math.abs(ix1 - ix2)
                int cellsHigh = Math.abs(iy1 - iy2)
                int expectedCells = cellsWide * cellsHigh

                if (expectedCells > 0) {
                    int actualCells = getGridSum(ix1, iy1, ix2, iy2)
                    if (actualCells == expectedCells) {
                        long actualArea = rectArea(v1, v2)
                        if (actualArea > localBest) {
                            localBest = actualArea
                            localBestJ = j
                        }
                    }
                }
            }

            [i, localBestJ, localBest]
        } as Callable
    }

    def searchResults = pool.invokeAll(searchTasks).collect { it.get() }
    def (bestI, bestJ, bestArea) = searchResults.max { it[2] }
    area2 = bestArea
    if (bestJ >= 0) {
        bestCorners = [vertices[bestI], vertices[bestJ]]
    }
} else {
    // Sekvensiell versjon
    for (int i = 0; i < n; i++) {
        def v1 = vertices[i]
        int ix1 = xMap[v1[0]]
        int iy1 = yMap[v1[1]]

        for (int j = i + 1; j < n; j++) {
            def v2 = vertices[j]
            int ix2 = xMap[v2[0]]
            int iy2 = yMap[v2[1]]

            int cellsWide = Math.abs(ix1 - ix2)
            int cellsHigh = Math.abs(iy1 - iy2)
            int expectedCells = cellsWide * cellsHigh

            if (expectedCells > 0) {
                int actualCells = getGridSum(ix1, iy1, ix2, iy2)
                if (actualCells == expectedCells) {
                    long actualArea = rectArea(v1, v2)
                    if (actualArea > area2) {
                        area2 = actualArea
                        bestCorners = [v1, v2]
                    }
                }
            }
        }
    }
}

// Rydd opp thread pool
pool?.shutdown()

def searchTime = System.currentTimeMillis() - searchStart
println "Søk: ${searchTime}ms"

println "Del 2: $area2 (mellom ${bestCorners[0]} og ${bestCorners[1]})"
println "\n" + "=" * 50
println "Total tid: ${System.currentTimeMillis() - totalStartTime}ms"
println "=" * 50

// Verifisering mot testdata
if (useTestData) {
    def pass1 = (area1 == testResult1)
    def pass2 = (area2 == testResult2)

    if (pass1 && pass2) {
        println "\n✓ Alle tester bestått!"
    } else {
        if (!pass1) println "✗ Del 1 feilet: forventet $testResult1, fikk $area1"
        if (!pass2) println "✗ Del 2 feilet: forventet $testResult2, fikk $area2"
        System.exit(1)
    }
}