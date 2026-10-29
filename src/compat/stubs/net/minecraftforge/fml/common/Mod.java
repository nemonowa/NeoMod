package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ビルド専用の代用品 (jar には入れない)。ForgeGate を Java 8 でコンパイルするために、本物の Forge の @Mod と同じ名前・同じ形で置く。
 * 本物の Forge の jar は新しい Java 用なので、Java 8 のコンパイラは読めない。実行時は、Forge 本物の @Mod が使われる。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}
