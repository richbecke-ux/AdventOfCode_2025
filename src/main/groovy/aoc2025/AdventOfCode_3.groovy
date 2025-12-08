package aoc2025

long calcBankMax(String spec, int activeCells) {
    def digitPos = (0..<activeCells).inject([]) { pos, i ->
        def start = pos ? pos[-1] + 1 : 0
        def end = spec.length() - activeCells + i
        pos << (start..end).max { spec[it] }
    }
    digitPos.collect { spec[it] }.join() as long
}


new File(args[0]).readLines().with { lines ->
    println "Total joltage part 1: " + lines.sum { calcBankMax(it.trim(), 2) }
    println "Total joltage part 2: " + lines.sum { calcBankMax(it.trim(), 12) }
}

/*
// "Problemløsningsversjon" - få mer eksplisitt tradisjonell Java-style kode til å funke før refaktorering til idiomatisk Groovy med Collections & closures
class BatteryBank {
    String bank
    long max

    BatteryBank (String spec, int activeCells) {
        bank = spec
        int length = bank.length()
        def digitPos = (0..<activeCells).collect {0}
        for (int i = 0; i < activeCells; ++i) {
            int start = (i > 0) ? digitPos[i - 1] + 1 : 0
            digitPos [i] = findLeftmostMax (start, length - (activeCells - i))
        }
        def maxStr = ""
        for (int i = 0; i < activeCells; ++i) {
            maxStr += bank[digitPos[i]]
        }
        max = maxStr as long
    }

    int findLeftmostMax (int start, int end) {
        def maxPos = start
        for (int j = start; j <= end; ++j) {
            if (bank[j] == '9') {
                maxPos = j
                break
            }
            if ((bank[j] as int) > (bank[maxPos] as int)) {
                maxPos = j
            }
        }
        return maxPos
    }
}

println "Total joltage part 1: " + new File(args[0]).readLines().sum { new BatteryBank(it.trim(), 2).max }
println "Total joltage part 2: " + new File(args[0]).readLines().sum { new BatteryBank(it.trim(), 12).max }
*/