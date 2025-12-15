package aoc2025.day9

import java.util.concurrent.*

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
// Innlesning og parsing av polygon-hjørner
// ============================================================

def totalStartTime = System.currentTimeMillis()

def vertices = (args ? new File(args[0]).text : testData)
        .readLines()
        .collect { it.split(',')*.toLong() }

def (xs, ys) = vertices.transpose()

println "X range: ${xs.min()} to ${xs.max()}, Y range: ${ys.min()} to ${ys.max()}"
println "Vertices: ${vertices.size()}"

// ============================================================
// Hjelpefunksjoner
// ============================================================

// Beregn areal av rektangel mellom to hjørner
// Bruker diskret geometri: antall celler = (avstand + 1)
def rectArea = { a, b ->
    ((a[0] - b[0]).abs() + 1) * ((a[1] - b[1]).abs() + 1)
}

// ============================================================
// PART 1: Største rektangel mellom to vilkårlige hjørner
//
// Løsning: Brute-force test av alle hjørnepar
// Kompleksitet: O(n²) hvor n er antall hjørner
// ============================================================

startTime = System.currentTimeMillis()

def area1 = vertices.withIndex()
        .collectMany { a, i ->
            vertices.drop(i + 1).collect { b -> rectArea(a, b) }
        }
        .max()

println "\nPart 1 rectangle area: $area1 (computed in ${System.currentTimeMillis() - startTime} ms)"
//if (!args) assert area1 == testResult1

// ============================================================
// PART 2: Største rektangel som ligger inne i polygonet
//
// Strategi:
// 1. Bruk Hanan-grid: Vertices deler planet i rektangulære regioner
// 2. For hver y-koordinat, finn x-intervaller som er innenfor polygonet
// 3. Test alle hjørnepar og sjekk om rektangelet passer i intervallene
//
// Nøkkelinnsikt: I diskret geometri med kanttykkelse 1 kan polygonet
// sees som overlappende rektangler. Vi tester punkter ved, over og
// under hver y-koordinat for å fange alle rektangler som berører den.
// ============================================================

def sortedYs = ys.unique().sort()
def sortedXs = xs.unique().sort()

println "\nHanan grid: ${sortedXs.size()} x-values × ${sortedYs.size()} y-values"
println "Total candidate pairs: ${vertices.size() * (vertices.size() - 1) / 2}"

// Thread-safe cache for punkt-i-polygon test
// Ray casting algoritme: Tell antall krysninger med polygonkanter
// Oddetall krysninger → inne, partall → ute
def polygonCache = new ConcurrentHashMap().withDefault { key ->
    def (px, py) = key
    def inside = false

    (0..<vertices.size()).each { i ->
        def (x1, y1) = vertices[i]
        def (x2, y2) = vertices[(i + 1) % vertices.size()]

        // Sjekk om horisontal stråle fra (px, py) krysser kanten
        if ((y1 > py) != (y2 > py)) {
            def xCross = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
            if (px < xCross) {
                inside = !inside  // Toggle ved hver krysning
            }
        }
    }
    inside
}

def pointInPolygon = { px, py -> polygonCache[[px, py]] }

// Bygg intervaller for hver y-koordinat i Hanan-grid
// Bruker parallellisering over tilgjengelige CPU-kjerner
final def sortedYsList = sortedYs
final def sortedXsList = sortedXs
//final def pointInPolygon = pointInPolygon

def threads = Runtime.runtime.availableProcessors()
def pool = Executors.newFixedThreadPool(threads)

println "\nBuilding intervals (using $threads threads)..."
startTime = System.currentTimeMillis()

def intervalsAtY = pool
        .invokeAll(sortedYsList.collect { y ->
            {
                def yIdx = sortedYsList.indexOf(y)
                def intervals = []
                def regionStart = null

                // For hver kolonne i Hanan-grid, test om den er innenfor polygonet
                (0..<sortedXsList.size() - 1).each { i ->
                    def x1 = sortedXsList[i]
                    def x2 = sortedXsList[i + 1]
                    def midX = (x1 + x2).intdiv(2)

                    // Test tre punkter for å fange overlappende rektangler:
                    // - Ved y selv (tester horisontale kanter)
                    // - Cellen over y (tester rektangel som starter ved y)
                    // - Cellen under y (tester rektangel som slutter ved y)
                    def isInside = pointInPolygon(midX, y) ||
                            (yIdx < sortedYsList.size() - 1 &&
                                    pointInPolygon(midX, (y + sortedYsList[yIdx + 1]).intdiv(2))) ||
                            (yIdx > 0 &&
                                    pointInPolygon(midX, (sortedYsList[yIdx - 1] + y).intdiv(2)))

                    // Bygg sammenhengende intervaller
                    if (isInside) {
                        if (regionStart == null) regionStart = x1
                    } else {
                        if (regionStart != null) {
                            intervals << [regionStart, x1]
                            regionStart = null
                        }
                    }
                }

                // Lukk åpent intervall ved siste x-koordinat
                if (regionStart != null) {
                    intervals << [regionStart, sortedXsList[-1]]
                }

                [(y): intervals]
            } as Callable
        })
        .collect { it.get() }
        .collectEntries()

pool.shutdown()

def buildTime = System.currentTimeMillis() - startTime
println "Intervals built in ${buildTime}ms"
println "Cache size: ${polygonCache.size()} unique points tested"
println "Average cache entries per y-value: ${(polygonCache.size() / sortedYs.size()).round(1)}"

// Sjekk om et rektangel ligger helt innenfor polygonet
// Rektangelet må passe innenfor minst ett intervall ved hver y-verdi det spenner over
def isContained = { a, b ->
    def xMin = Math.min(a[0], b[0])
    def xMax = Math.max(a[0], b[0])
    def yMin = Math.min(a[1], b[1])
    def yMax = Math.max(a[1], b[1])

    sortedYs
            .findAll { it >= yMin && it <= yMax }
            .every { y ->
                intervalsAtY[y]?.any { iv ->
                    xMin >= iv[0] && xMax <= iv[1]
                } ?: false
            }
}

println "\nSearching for largest inscribed rectangle..."
startTime = System.currentTimeMillis()

// Brute-force søk gjennom alle hjørnepar
// Kompleksitet: O(n² × m) hvor m er antall y-verdier per rektangel
def (area2, corner1, corner2) = vertices.withIndex()
        .collectMany { a, i ->
            vertices.drop(i + 1).findResults { b ->
                isContained(a, b) ? [rectArea(a, b), a, b] : null
            }
        }
        .max { it[0] }

def searchTime = System.currentTimeMillis() - startTime
println "Search completed in ${searchTime}ms"

println "Part 2 rectangle area: $area2 (between $corner1 and $corner2)"
println "\nScript completed in ${System.currentTimeMillis() - totalStartTime}ms"

//if (!args) assert area2 == testResult2