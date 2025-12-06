class Range {
    long rangeStart, rangeEnd, sum = 0

    boolean inRange(long num) { num in rangeStart..rangeEnd }

    int numDigits(long val) { (val as String).length() }

    List<Integer> findDivisors(int num) { (1..<num).findAll { num % it == 0 } }

    boolean isRepeating(int digits, long num) {
        if (digits == 1) return false
        def str = num as String
        findDivisors(digits).any { div ->
            (0..<(digits - div)).step(div).every { i ->
                str[i..<(i + div)] == str[(i + div)..<(i + 2 * div)]
            }
        }
    }

    void processSubRange(int digits, long start, long end) {
        if (digits == 1) return
        def (startStr, endStr) = [start, end]*.toString()
        findDivisors(digits).each { div ->
            def (secStart, secEnd) = [startStr, endStr].collect { it[0..<div] as long }
            (secStart..secEnd).each { i ->
                if (!isRepeating(div, i)) {
                    def section = i as String
                    def canvas = section * (digits.intdiv(div))
                    def num = canvas as long
                    if (inRange(num)) sum += num
                }
            }
        }
    }

    Range(String spec) {
        (rangeStart, rangeEnd) = spec.split('-').collect { it as long }
        (numDigits(rangeStart)..numDigits(rangeEnd)).each { digits ->
            def start = digits == numDigits(rangeStart) ? rangeStart : 10 ** (digits - 1)
            def end = digits == numDigits(rangeEnd) ? rangeEnd : 10 ** digits - 1
            processSubRange(digits, start, end)
        }
    }
}

def testInput = '''11-22,95-115,998-1012,1188511880-1188511890,222220-222224,1698522-1698528,446443-446449,38593856-38593862,565653-565659,824824821-824824827,2121212118-2121212124'''
def testAnswer_1 = 1227775554
def testAnswer_2 = 4174379265

def input = '''92916254-92945956,5454498003-5454580069,28-45,4615-7998,4747396917-4747534264,272993-389376,36290651-36423050,177-310,3246326-3418616,48-93,894714-949755,952007-1003147,3-16,632-1029,420-581,585519115-585673174,1041-1698,27443-39304,71589003-71823870,97-142,2790995-2837912,579556301-579617006,653443-674678,1515120817-1515176202,13504-20701,1896-3566,8359-13220,51924-98061,505196-638209,67070129-67263432,694648-751703,8892865662-8892912125'''

assert testInput.tokenize(',').sum { new Range(it).sum } == testAnswer_2

println "Sum of invalid IDs: " + input.tokenize(',').sum { new Range(it).sum }
