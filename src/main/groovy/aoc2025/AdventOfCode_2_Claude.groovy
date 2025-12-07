package aoc2025

def input = '''92916254-92945956,5454498003-5454580069,28-45,4615-7998,4747396917-4747534264,272993-389376,36290651-36423050,177-310,3246326-3418616,48-93,894714-949755,952007-1003147,\
3-16,632-1029,420-581,585519115-585673174,1041-1698,27443-39304,71589003-71823870,97-142,2790995-2837912,579556301-579617006,653443-674678,1515120817-1515176202,13504-20701,1896-3566,8359-13220,\
51924-98061,505196-638209,67070129-67263432,694648-751703,8892865662-8892912125'''

// Check if this base is the smallest repeating unit for a number of given total length.
// This prevents counting the same number multiple times when iterating different 'times' values.
//
// Example: "12121212" (length 8) can be seen as:
//   - "1212" repeated 2 times (base length 4)
//   - "12" repeated 4 times (base length 2)
//
// When we process times=2, we get base="1212". But "1212" itself is "12" repeated twice,
// so isSmallestUnit("1212", 8) returns false - we skip it.
// When we process times=4, we get base="12". "12" cannot be decomposed further,
// so isSmallestUnit("12", 8) returns true - we count it.
//
// This ensures each repeated-pattern number is counted exactly once: via its smallest base.
def isSmallestUnit(String base, int totalLen) {
    def baseLen = base.length()
    // Check all possible smaller lengths that divide the total length
    !(1..<baseLen).any { smallerLen ->
        totalLen % smallerLen == 0 && base == base[0..<smallerLen] * (baseLen / smallerLen)
    }
}

// Sum all repeated-pattern numbers in range [lo, hi] where a base is repeated 'times' times.
//
// Instead of iterating through every number in [lo, hi] and checking if it's a repeated pattern
// (which would be impossibly slow for ranges like 5454498003-5454580069), we flip the approach:
// generate all possible repeated-pattern numbers and check if they fall within the range.
//
// For a given 'times', a repeated number has total length divisible by 'times'.
// E.g., for times=3, valid lengths are 3, 6, 9, 12... (base lengths 1, 2, 3, 4...)
//
// checkSmallest: if true, only count numbers where the base is the smallest repeating unit
//                (used in Part 2 to avoid counting same number via different 'times' values)
def sumRepeatedInRange(BigInteger lo, BigInteger hi, int times, boolean checkSmallest) {
    def loLen = lo.toString().length()
    def hiLen = hi.toString().length()
    def total = 0G

    // Only consider total lengths divisible by 'times'
    // E.g., for times=2: lengths 2, 4, 6, 8... (bases of length 1, 2, 3, 4...)
    (loLen..hiLen).findAll { it % times == 0 }.each { totalLen ->
        def baseLen = totalLen / times as int

        // Base must not have leading zeros, so minimum is 10^(baseLen-1)
        // E.g., for baseLen=3: min=100, max=999
        def minBase = (10G ** (baseLen - 1)).max(1G)  // .max(1G) handles baseLen=1 case
        def maxBase = 10G ** baseLen - 1

        // Iterate through all possible base patterns of this length
        (minBase..maxBase).each { base ->
            def baseStr = base.toString()

            // If checkSmallest is true, skip bases that aren't the smallest unit
            if (!checkSmallest || isSmallestUnit(baseStr, totalLen)) {
                // Create the repeated number: e.g., "12" * 3 = "121212"
                def repeated = (baseStr * times).toBigInteger()

                // Check if it falls within our target range
                if (repeated >= lo && repeated <= hi) {
                    total += repeated
                }
            }
        }
    }
    total
}

// Part 1: Find numbers that are a pattern repeated exactly twice
// No deduplication needed since we only check times=2
def solveP1(String input) {
    input.split(',')
            .collect { it.split('-')*.toBigInteger() }  // Parse "11-22" into [11, 22]
            .sum(0G) { pair ->
                def (lo, hi) = pair
                sumRepeatedInRange(lo, hi, 2, false)
            }
}

// Part 2: Find numbers that are a pattern repeated 2 or more times
// Uses checkSmallest=true to avoid counting same number multiple times
// E.g., "111111" matches times=2,3,6 but we only count it once (via times=6, base="1")
def solveP2(String input) {
    input.split(',')
            .collect { it.split('-')*.toBigInteger() }
            .sum(0G) { pair ->
                def (lo, hi) = pair
                def maxLen = hi.toString().length()
                // Try all possible repetition counts from 2 up to maxLen
                // (can't repeat more times than total digits)
                (2..maxLen).sum(0G) { times -> sumRepeatedInRange(lo, hi, times, true) }
            }
}

println "Part 1: ${solveP1(input)}"
println "Part 2: ${solveP2(input)}"