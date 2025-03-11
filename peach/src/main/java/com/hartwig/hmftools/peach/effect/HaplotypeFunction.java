package com.hartwig.hmftools.peach.effect;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class HaplotypeFunction
{
    public abstract String geneName();
    public abstract String haplotypeName();
    public abstract String function();
}
