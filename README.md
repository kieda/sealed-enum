# sealed-enum
Java 20 library to use sealed classes as if they were enums! 
Enums are great but they can't handle generic arguments. This is where sealed classes come in.
## Example usage
```
public sealed MySealedEnum extends SealedEnum<T>{
   // this can only be called once!
   public static MySealedEnum INSTANCE = new MySealedEnum();
   private static final class EnumOptionA extends MySealedEnum{}
   private static final class EnumOptionB extends MySealedEnum{}
   private static final class EnumOptionC extends MySealedEnum{}
   
   {
      System.out.println(INSTANCE.values()); // EnumOptionA, EnumOptionB, EnumOptionC. 
      MySealedEnum enumOption = SealedEnum.getSealedEnum(EnumOptionA.class);
      switch(enumOption) {
         case EnumOptionA -> ...
         case EnumOptionB -> ...
         default -> ...
      }
   }
}
```

Please see `SealedEnumExample` for a decent example how it can be used with generics!

## todo / other thoughts

### possible naughty usage

```
public sealed MySealedEnum extends SealedEnum<T>{
    protected MySealedEnum() {
        super(MySealedEnum.class)
        SealedEnum badInstance = new SealedEnum(MySealedEnum.class);
        SealedEnum anotherBadInstance = new MySealedEnum();
            // attempting to circumvent security checks :(
    }

    public static final Enum1 extends MySealedEnum{
        public Enum1(otherArgs) {
            super(otherArgs);
        }
        public Enum1() {
            this(someStaticFunction());
            Enum1 badInstance = new Enum1();
        }
    }
}
```
What we do: have a set of the classes we have "seen" by calling `getClass()` in
`SealedEnum`'s constructor. If there is already a class we have seen, this implies
we are attempting to create the same object for the class twice. We throw an exception
as a result.

### todo

* todo: could we just have `SealedEnum myEnum = SealedEnum.of(MySealedEnum.class);`
    * I like this better. We can also return the same object using this method.
    * This would also not have an instantiation of the base class like we currently do.
    * What would this API look like?
    * `interface EnumDescriptor<T extends SealedEnum>` -- interface common to enum values and base type
      * `+ values() : List<T>`
      * `+ ordinal() : int`
      * `+ getDelaringClass(): Class<? super T>`
    * `SealedEnumType<T extends SealedEnum> implements EnumDescriptor<T>` -- handle used to describe base class
    * `MyEnumVal extends MyEnum` -- user implementation of an enum's val
    * `MyEnum extends SealedEnum` -- user implementation of an enum
    * `SealedEnum implements EnumDescriptor`
    * `SealedEnumType::values() : MyEnum[]` -- returns values possible
    * `SealedEnumType::ordinal() : int` -- returns -1
    * `SealedEnumType::getDeclaringClass()` -- returns (this) base class
    * `SealedEnum::ordinal() : int`
    * `SealedEnum::getDeclaringClass() : MyEnum.class`
    * `SealedEnum.getInstance(MyEnumVal.class) : MyEnumVal (extends SealedEnum)`
    * `SealedEnum.getSealedEnum(MyEnum.class) : SealedEnumType<MyEnum>`
    * `SealedEnum.values(MyEnum.class | MyEnumVal.class) : MyEnum[]`
    * `SealedEnum.ordinal(MyEnum.class | MyEnumVal.class) : int`
    * `SealedEnum.declaringClass(MyEnumVal.class | MyEnum.class) : MyEnum.class`
