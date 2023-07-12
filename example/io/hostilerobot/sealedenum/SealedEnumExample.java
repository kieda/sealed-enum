package io.hostilerobot.sealedenum;

import java.util.function.Function;
import java.util.function.Predicate;

public class SealedEnumExample {
    public interface Next<A, P extends PipeLine>{
        public boolean stop(A cond);
        public P next();
    }
    public static final PipeLine PIPELINE = new PipeLine(x -> true);

    public static sealed class PipeLine<A, B, P extends PipeLine<B, ?, ?>> extends SealedEnum<PipeLine<A, B, P>> implements Function<A, B>, Next<A, P> {
        private final Predicate<A> doStop;
        protected PipeLine(Predicate<A> doStop) {
            super(PipeLine.class);
            this.doStop = doStop;
        }

        @Override
        public B apply(A a) {
            throw new BaseEnumException();
        }

        @Override
        public P next() {
            throw new BaseEnumException();
        }
        public boolean stop(A cond) {
            return doStop.test(cond);
        }
    }

    public static final class END extends PipeLine<Integer, Boolean, PipeLine<Boolean, ?, ?>> {
        protected END() { super(x-> x < 0); } // stop if x is less than zero
        @Override
        public Boolean apply(Integer integer) { return integer % 2 == 0;}
        @Override
        public PipeLine<Boolean, ?, ?> next() { return null; }
    }
    public static final class START extends PipeLine<String, Integer, END> {
        protected START() { super(s -> s == null || s.length() < 5); }
        @Override
        public Integer apply(String s) { return Integer.parseInt(s); }
        @Override
        public END next() { return getSealedEnum(END.class); }
    }

    public static Object run(String input) {
        Object state = input;
        PipeLine line = SealedEnum.getSealedEnum(START.class);
        while(line != null && !line.stop(state)) {
            state = line.apply(state);
            line = line.next();
        }
        return state;
    }
    public static PipeLine runState(String input) {
        Object state = input;
        PipeLine line = SealedEnum.getSealedEnum(START.class);
        while(line != null && !line.stop(state)) {
            state = line.apply(state);
            line = line.next();
        }
        return line;
    }

    public static void printRun(String input) {
        Object result = run(input);
        System.out.println(runState(input) + " " + (result == null ? null : result.getClass().getSimpleName()) + " " + result);
    }

    public static void main(String[] args) {
        System.out.println(PIPELINE.values()); // [END, START]
        printRun(null); // START, val is null
        printRun("1234"); // START,  "1234", length < 5
        printRun("-898"); // START, "-898", length < 5
        printRun("-8989"); // END, -8989, val < 0
        printRun("12345"); // null, false, is odd
        printRun("12346"); // null, true
        printRun("not a number"); // exception, not a number
    }
}
