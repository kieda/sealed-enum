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
        protected END() {
            super(x-> x < 0); // stop if x is less than zero
        }

        @Override
        public Boolean apply(Integer integer) {
            return integer % 2 == 0;
        }

        @Override
        public PipeLine<Boolean, ?, ?> next() {
            return null;
        }
    }
    public static final class START extends PipeLine<String, Integer, END> {
        protected START() {
            super(s -> s == null || s.length() < 5);
        }

        @Override
        public Integer apply(String s) {
            return Integer.parseInt(s);
        }
        @Override
        public END next() {
            return getSealedEnum(END.class);
        }
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

    public static void main(String[] args) {
        System.out.println(PIPELINE.values()); // [END, START]
        System.out.println(run(null)); // null, val is null
        System.out.println(run("1234")); // "1234", length < 5
        System.out.println(run("-898")); // "-898", length < 5
        System.out.println(run("-8989")); // -8989, val < 0
        System.out.println(run("12345")); // false, is odd
        System.out.println(run("12346")); // true
        System.out.println(run("not a number")); // exception, not a number
    }
}
