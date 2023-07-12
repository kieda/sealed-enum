package io.hostilerobot.sealedenum;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * constructs enum-like instances for a sealed class. We keep a thread-safe static map to track enum instances
 * and to guarantee there is only one instance
 */
public class SealedEnum<T extends SealedEnum<T>> implements Comparable<T>{
    // problem: we only want one map at the level of super-class
    // currently this will fill toOrdinal and instances for each subclass, which we don't want to do
    // todo - could we just have SealedEnum myEnum = SealedEnum.of(MySealedEnum.class); -- I like this better. We can also return the same object using this method.
    // myEnum.values()


    /* static types:
     * SealedEnumBase : T
     * SealedEnum : I extends T
     * SealedEnumBaseClass: Class<T>
     * SealedEnumClass : Class<I>
     *
     * Lookup types:
     *   baseToBaseInstance: SealedEnumBaseClass -> SealedEnumBase
     *   instanceToBase:     SealedEnumClass -> SealedEnumBaseClass
     *   baseToOrdinals:     SealedEnumBaseClass -> (SealedEnumClass -> ordinal:int)
     *   baseToInstances:    SealedEnumBaseClass -> (ordinal:int -> SealedEnum)
     */

    private static final Map<Class<? extends SealedEnum<?>>, SealedEnum<?>> baseToBaseInstance // base enum class to actual instance
            = new Hashtable<>();

    // maps enum subclass to its base class
    // we use this over having a private variable, since we would have to set the private variable after the constructor has completed
    // and there might be constructor logic that requires operations like getValues()
    private static final Map<Class<? extends SealedEnum<?>>, Class<? extends SealedEnum<?>>> instanceToBase
            = new Hashtable<>();

    private static final Map<Class<? extends SealedEnum<?>>, Map<Class<? extends SealedEnum<?>>, Integer>> baseToOrdinals
            = new Hashtable<>();
    private static final Map<Class<? extends SealedEnum<?>>, List<? extends SealedEnum<?>>> baseToInstances
            = new Hashtable<>();

    private static final Map<Class<? extends SealedEnum<?>>, Boolean> SEEN = new Hashtable<>();

    private final boolean isBase;

    private static <T extends SealedEnum<T>> boolean hasBaseEntry(Class<T> base) {
        return baseToBaseInstance.containsKey(base);
    }

    private static <T extends SealedEnum<T>> boolean isDuplicateEntry(Class<? extends T> enumVal) {
        return SEEN.containsKey(enumVal);
    }
    private static <T extends SealedEnum<T>> boolean hasEnumEntry(Class<? extends T> enumVal) {
        Class<T> base = (Class<T>) instanceToBase.get(enumVal);
        if(base == null)
            return false;
        T baseEnum = getSealedEnum(base);
        List<? extends SealedEnum<?>> inst = baseToInstances.get(base);
        if(inst == null)
            return false;
        Map<Class<? extends SealedEnum<?>>, Integer> getOrdinal = baseToOrdinals.get(base);
        if(getOrdinal == null)
            return false;
        Integer ordinal = getOrdinal.get(enumVal);
        if(ordinal == null)
            return false;
        return ordinal >= 0 && ordinal < inst.size() && inst.get(ordinal) != null;
    }


    private static <T extends SealedEnum<T>> void putEntries(Class<T> k, T v,
                                                             Map<Class<? extends SealedEnum<?>>, Integer> ordinals,
                                                             List<T> inst) {

        //java.util.Map<java.lang.Class<? extends T>,java.lang.Integer>
        // cannot be converted to
        // java.util.Map<java.lang.Class<? extends io.hostilerobot.ceramicrelief.util.SealedEnum<?>>,
        // java.lang.Integer>
        baseToBaseInstance.put(k, v);
        baseToOrdinals.put(k, ordinals);
        baseToInstances.put(k, inst);
    }
    private static <T extends SealedEnum<T>> void clearEntries(Class<T> k) {

        //java.util.Map<java.lang.Class<? extends T>,java.lang.Integer>
        // cannot be converted to
        // java.util.Map<java.lang.Class<? extends io.hostilerobot.ceramicrelief.util.SealedEnum<?>>,
        // java.lang.Integer>
        baseToBaseInstance.remove(k);
        baseToOrdinals.remove(k);
        baseToInstances.remove(k);
    }



    //    private static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static Set<StackWalker.Option> ALL_OPTIONS = EnumSet.allOf(StackWalker.Option.class);

    /*
         thought experiment on naughty usage:

         public sealed MySealedEnum extends SealedEnum<T>{
            todo -- support multiple constructors HERE.
            protected MySealedEnum() {
                super(MySealedEnum.class)
                SealedEnum badInstance = new SealedEnum(MySealedEnum.class);
                SealedEnum anotherBadInstance = new MySealedEnum();
                    // we might be able to circumvent our security checks :(
            }

            public static final Enum1 extends MySealedEnum{
                public Enum1(otherArgs) {
                    super(otherArgs);
                }
                public Enum1() {
                    this(someStaticFunction());
                    new Enum1();
                }
            }

            // we can basically follow the type hierarchy in the stack heirarchy and ensure it's an exact match
            // after that we ensure that it's SealedEnum, then back to base

            stacktrace:
               MySealedEnum (constructor)
               SealedEnum (constructor)
               [reflect] Enum1.newInstance()
               Enum1 (constructor)
                   MySealedEnum (constructor) <Enum1>
                   SealedEnum (constructor) <Enum1>
                      ... stop from checks
                   new Enum1() (constructor) <newInst>
                      MySealedEnum (constructor) <newInst>
                      SealedEnum (constructor) <newInst>, current.
         }
         */

    protected SealedEnum(Class<T> base) {
        if(!base.isSealed())
            throw new IllegalArgumentException(base + " is not sealed!");
        // base must directly extend SealedEnum and pass itself as a parameter
        if(base.getSuperclass() != SealedEnum.class) {
            throw new IllegalArgumentException(base + " must directly extend " + getClass().getSimpleName());
        }
        Class<?> caller = StackWalker.getInstance(ALL_OPTIONS).getCallerClass();
        if(caller != base) {
            throw new IllegalArgumentException("caller " + caller + " must be of type " + base);
        }
        if(!base.isAssignableFrom(getClass())) {
            // we must have this instanceof T
            // prevents creating SealedEnum directly
            throw new IllegalArgumentException(getClass() + " is not of type " + base);
        }


        /* there are only two cases where this constructor should be called.
         * 1. by the constructor of the enum class
         *    MyEnum extends SealedEnum<MyEnum> {
         *       public static MyEnum INSTANCE = new MyEnum();
         *    }
         * 2. by this class via reflection
         *       Class<MyEnumCase1>.getDeclaredConstructor().newInstance()
         * we check the stacktrace to ensure the instance was constructed this way
         */

        isBase = base == getClass();

        // if(base == getClass())
        //    -- only permit this constructor to be called once for base
        // else
        //    getClass() is a subclass of the enum. The map should already be generated. If not, someone is attempting to instantiate an enum value directly. prevent this
        //    what if someone instantiates an enum subclass AFTER map entry is filled, not from this class's reflection?
        ok:{
            // isBase && hasEntry then we have already processed base => error
            // isBase && !hasEntry then we should process base since it's the first time => continue
            // !isBase && hasEntry && hasEnumEntry(subClass) then we already processed subclass, someone's attempting to instantiate outside of reflection => error
            // !isBase && hasEntry && !hasEnumEntry(subClass) then enum will be added => OK
            // !isBase && !hasEntry then we are processing subclass too early => error

            // todo - how do we want to deal with sealed classes that are not final?
            //    possibly: build a tree
            Class<?> duplicateOffender = base;
            boolean hasEntry = hasBaseEntry(base);
            duplicateError: // base should always be created first and exactly once.
            {
                if (!isDuplicateEntry(getClass())) {
                    synchronized (base) {
                        if(!isDuplicateEntry(getClass())) {
                            SEEN.put((Class<? extends SealedEnum<?>>) getClass(), true);
                        } else {
                            duplicateOffender = getClass();
                            break duplicateError;
                        }
                    }
                } else {
                    duplicateOffender = getClass();
                    break duplicateError;
                }

                if (isBase && !hasEntry) {
                    synchronized (base) {
                        hasEntry = hasBaseEntry(base);
                        // lock and check again.
                        if (!hasEntry) {
                            // if we still don't have the entry, then create all subclasses
                            Class<?>[] permitted = base.getPermittedSubclasses();
                            final int enumCount = permitted.length;
                            Map<Class<? extends SealedEnum<?>>, Integer> ordinalsForBase = new HashMap<>(enumCount);
                            List<T> instancesForBase = new ArrayList<>(enumCount);

                            // optimistically place entries. revert later if there's a problem
                            putEntries(base, (T) this, ordinalsForBase, instancesForBase);
                            int ordinal = 0;
                            try {
                                for (; ordinal < enumCount; ordinal++) {
                                    Class<? extends T> subClass = (Class<? extends T>) permitted[ordinal];
                                    if (!Modifier.isFinal(subClass.getModifiers())) {
                                        // todo - consider how we could utilize non-final subclasses
                                        //        and perhaps build a tree (?)
                                        throw new ReflectiveOperationException("subclasses must be final");
                                    }

                                    instanceToBase.put(subClass, base);
                                    ordinalsForBase.put(subClass, ordinal);

                                    //ordinalsForBase.put(subClass, ordinal);
                                    Constructor<? extends T> constructor = subClass.getDeclaredConstructor();
                                    if (!constructor.canAccess(null) && !constructor.trySetAccessible()) {
                                        throw new SecurityException("cannot access SealedEnum constructor " + constructor);
                                    }
                                    T enumInstance = constructor.newInstance();
                                    instancesForBase.add(enumInstance);
                                }
                                break ok;
                            } catch (InvocationTargetException ex) {
                                if (ex.getCause() instanceof RuntimeException rx) {
                                    throw rx;
                                } else {
                                    throw new IllegalArgumentException(ex.getCause());
                                }
                            } catch (ReflectiveOperationException ex) {
                                // undo entries
                                clearEntries(base);
                                // undo base class mapping
                                for (int j = 0; j < ordinal; j++) {
                                    Class<? extends T> subClass = (Class<? extends T>) permitted[j];
                                    instanceToBase.remove(subClass);
                                }

                                throw new IllegalArgumentException("all enums values must have zero-arg accessible constructors", ex);
                            }
                        } else {
                            // put the break explicitly here even though it falls through to exception without it
                            // this case occurs if another thread initialized base in between the lock time
                            // when this shouldn't happen!
                            break duplicateError;
                        }
                    }
                } else if (!isBase && hasEntry && hasEnumEntry(getClass())) {
                    duplicateOffender = getClass();
                    break duplicateError;
                } else if (isBase != hasEntry) {
                    // if this is the base, then we're attempting to initialize base a second time
                    break ok;
                }
            }

            throw new IllegalStateException("attempting to initialize SealedEnum " + duplicateOffender.getSimpleName() + " more than once");
        }
    }

    public final List<T> values() {
        Class<?> base = isBase ? getClass() : instanceToBase.get(getClass());
        // todo - possibly cache value on base instance
        // todo - add in isBaseClass as a final member
        return (List<T>)Collections.unmodifiableList(baseToInstances.get(base));
    }
    public final int ordinal() {
        if(isBase)
            return -1;
        else
            return baseToOrdinals.get(instanceToBase.get(getClass())).get(getClass());
    }

    public final T instance(Class<? extends T> clazz) {
        Class<?> base = isBase ? getClass() : instanceToBase.get(getClass());
        // these casts will pass given the guarantee from the constructor
        if(clazz == base)
            return (T)this;
        else
            return (T) baseToInstances.get(base).get(baseToOrdinals.get(base).get(clazz));
    }

    public static <T> T getSealedEnum(Class<? extends T> clazz) {
        // returns the cached enum (base class)
        return (T) baseToBaseInstance.get(clazz);
    }

    @Override
    public final int compareTo(T o) {
        if(o == null)
            return 1; // other is less than this
        return Integer.compare(this.ordinal(), o.ordinal());
    }

    @Override
    public boolean equals(Object other) {
        return this == other; // only one instance :)
    }

    public final int hashCode() {
        return super.hashCode();
    }

    protected final Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public final String toString() {
        if(isBase) {
            return "Base<" + getClass().getSimpleName() + ">";
        } else {
            return getClass().getSimpleName();
        }
    }
}
