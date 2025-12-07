package aoc2025

class Range {
    long rangeStart, rangeEnd, sum_1 = 0, sum_2 = 0

    boolean inRange(long num) { num in rangeStart..rangeEnd }

    List<Integer> findDivisors(int num) { (1..<num).findAll { num % it == 0 } }

    boolean isRepeating(long value, int digits) {
        if (digits == 1) return false
        def valStr = value as String
        findDivisors(digits).any { div ->
            valStr[0..<div] * (digits/div) == valStr
        }
    }

    void processSubRange(int digits, long start, long end) {
        if (digits == 1) return
        def (startStr, endStr) = [start, end]*.toString()
        findDivisors(digits).each { div ->
            def (secStart, secEnd) = [startStr, endStr].collect { it[0..<div] as long }
            (secStart..secEnd).each { i ->
                if (!isRepeating(i, div)) {
                    def section = i as String
                    def canvas = section * (digits.intdiv(div))
                    def num = canvas as long
                    if (inRange(num)) {
                        sum_2 += num
                        if ((digits % 2 == 0) && (canvas[0..<(digits/2)] == canvas[(digits/2)..<digits])) {
                            sum_1 += num
                        }
                    }
                }
            }
        }
    }

    Range(String spec) {
        def (startStr, endStr) = spec.split('-')
        def (startDigits, endDigits) = [startStr, endStr]*.length()
        (rangeStart, rangeEnd) = [startStr, endStr].collect { it as long }
        (startDigits..endDigits).each { digits ->
            def start = digits == startDigits ? rangeStart : 10 ** (digits - 1)
            def end = digits == endDigits ? rangeEnd : 10 ** digits - 1
            processSubRange(digits, start, end)
        }
    }
}

def testInput = '''11-22,95-115,998-1012,1188511880-1188511890,222220-222224,1698522-1698528,446443-446449,38593856-38593862,565653-565659,824824821-824824827,2121212118-2121212124'''
def testAnswer_1 = 1227775554
def testAnswer_2 = 4174379265

def input = '''92916254-92945956,5454498003-5454580069,28-45,4615-7998,4747396917-4747534264,272993-389376,36290651-36423050,177-310,3246326-3418616,48-93,894714-949755,952007-1003147,3-16,632-1029,420-581,585519115-585673174,1041-1698,27443-39304,71589003-71823870,97-142,2790995-2837912,579556301-579617006,653443-674678,1515120817-1515176202,13504-20701,1896-3566,8359-13220,51924-98061,505196-638209,67070129-67263432,694648-751703,8892865662-8892912125'''

assert testInput.tokenize(',').collect { new Range(it) }.with { [sum { it.sum_1 }, sum { it.sum_2 }] } == [testAnswer_1, testAnswer_2]

def (p1, p2) = input.tokenize(',').collect { new Range(it) }.with { [sum { it.sum_1 }, sum { it.sum_2 }] }
println "Part 1: $p1\nPart 2: $p2"