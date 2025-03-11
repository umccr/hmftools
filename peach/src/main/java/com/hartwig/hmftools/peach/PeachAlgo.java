package com.hartwig.hmftools.peach;

import com.hartwig.hmftools.peach.haplotype.HaplotypeCombination;
import com.hartwig.hmftools.peach.panel.HaplotypePanel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.hartwig.hmftools.peach.PeachUtils.GERMLINE_TOTAL_COPY_NUMBER;
import static com.hartwig.hmftools.peach.PeachUtils.PCH_LOGGER;

public class PeachAlgo
{
    private final HaplotypePanel haplotypePanel;

    public PeachAlgo(final HaplotypePanel haplotypePanel)
    {
        this.haplotypePanel = haplotypePanel;
    }

    public Map<String, HaplotypeAnalysis> getGeneToHaplotypeAnalysis(final Map<String, Integer> eventIdToCount)
    {
        return haplotypePanel.getGenes().stream().collect(Collectors.toMap(g -> g, g -> getHaplotypeAnalysis(eventIdToCount, g)));
    }

    private HaplotypeAnalysis getHaplotypeAnalysis(final Map<String, Integer> eventIdToCount, final String gene)
    {
        PCH_LOGGER.info("handle gene: {}", gene);
        Map<String, Integer> relevantEventIdToCount = selectRelevantEvents(eventIdToCount, gene);
        List<HaplotypeCombination> possibleHaplotypeCombinations = HaplotypeCaller.findPossibleHaplotypeCombinations(
                relevantEventIdToCount, haplotypePanel.getNonDefaultHaplotypes(gene), haplotypePanel.getDefaultHaplotype(gene));
        List<HaplotypeCombination> bestCombinations =
                selectBestCombinations(possibleHaplotypeCombinations, haplotypePanel.getWildTypeHaplotypeName(gene));
        PeachQCStatus qcStatus = determineQcStatus(relevantEventIdToCount, bestCombinations);
        HaplotypeCombination bestHaplotypeCombination = hasBestHaplotypeCombination(qcStatus) ? bestCombinations.get(0) : null;

        return new HaplotypeAnalysis(
                relevantEventIdToCount,
                possibleHaplotypeCombinations,
                haplotypePanel.getDefaultHaplotype(gene).getName(),
                haplotypePanel.getWildTypeHaplotypeName(gene),
                qcStatus,
                bestHaplotypeCombination
        );
    }

    private Map<String, Integer> selectRelevantEvents(final Map<String, Integer> eventIdToCount, final String gene)
    {
        return eventIdToCount.entrySet()
                .stream()
                .filter(e -> haplotypePanel.isRelevantFor(e.getKey(), gene))
                .collect(toMapWithNull(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static List<HaplotypeCombination> selectBestCombinations(
            final List<HaplotypeCombination> haplotypeCombinations, final String wildTypeHaplotypeName)
    {
        if(haplotypeCombinations.isEmpty())
        {
            return Collections.emptyList();
        }

        int minimumHaplotypeCountDeviation =
                haplotypeCombinations.stream().mapToInt(c -> Math.abs(c.getHaplotypeCount() - GERMLINE_TOTAL_COPY_NUMBER)).min().getAsInt();
        List<HaplotypeCombination> candidates = haplotypeCombinations.stream()
                .filter(c -> Math.abs(c.getHaplotypeCount() - GERMLINE_TOTAL_COPY_NUMBER) == minimumHaplotypeCountDeviation)
                .collect(Collectors.toList());

        if(candidates.size() <= 1)
        {
            return candidates;
        }

        int minimumNonWildTypeCount =
                candidates.stream().mapToInt(c -> c.getHaplotypeCountWithout(wildTypeHaplotypeName)).min().getAsInt();
        return candidates.stream()
                .filter(c -> c.getHaplotypeCountWithout(wildTypeHaplotypeName) == minimumNonWildTypeCount)
                .collect(Collectors.toList());
    }

    private static PeachQCStatus determineQcStatus(
            final Map<String, Integer> eventIdToCount, final List<HaplotypeCombination> bestHaplotypeCombinations)
    {
        if(eventIdToCount.values().stream().anyMatch(Objects::isNull))
        {
            return PeachQCStatus.FAIL_EVENT_WITH_UNKNOWN_COUNT;
        }
        else if(bestHaplotypeCombinations.isEmpty())
        {
            return PeachQCStatus.FAIL_NO_COMBINATION_FOUND;
        }
        else if(bestHaplotypeCombinations.size() != 1)
        {
            return PeachQCStatus.FAIL_NO_UNIQUE_BEST_COMBINATION_FOUND;
        }
        else if(bestHaplotypeCombinations.get(0).getHaplotypeCount() > 2)
        {
            return PeachQCStatus.WARN_TOO_MANY_ALLELES_FOUND;
        }
        else
        {
            return PeachQCStatus.PASS;
        }
    }

    public static boolean hasBestHaplotypeCombination(final PeachQCStatus status)
    {
        switch(status)
        {
            case PASS:
            case WARN_TOO_MANY_ALLELES_FOUND:
                return true;
            case FAIL_NO_COMBINATION_FOUND:
            case FAIL_NO_UNIQUE_BEST_COMBINATION_FOUND:
            case FAIL_EVENT_WITH_UNKNOWN_COUNT:
                return false;
            default:
                throw new RuntimeException(String.format("Unrecognized QC status encountered: %s", status));
        }
    }

    private static <T, K, U> Collector<T, ?, Map<K, U>> toMapWithNull(
            final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper)
    {
        // builtin toMap Collector cannot handle null values
        // source for this code: https://stackoverflow.com/questions/24630963/nullpointerexception-in-collectors-tomap-with-null-entry-values/32648397#32648397
        return Collectors.collectingAndThen(Collectors.toList(), list ->
        {
            Map<K, U> result = new HashMap<>();
            for(T item : list)
            {
                K key = keyMapper.apply(item);
                if(result.putIfAbsent(key, valueMapper.apply(item)) != null)
                {
                    throw new IllegalStateException(String.format("Duplicate key %s", key));
                }
            }
            return result;
        });
    }
}
