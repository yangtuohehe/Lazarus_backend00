package com.example.lazarus_backend00.domain.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.BitSet;

/**
 * 像元级时空状态块 - 严格遵循父类 TSShell 空间定义的净化版
 */
public class TSState extends TSShell {

    @JsonIgnore
    private BitSet readyMask;

    @JsonIgnore
    private BitSet replacedMask;

    public TSState() { super(); }

    public TSState(TSShell baseShell, DataState initState) {
        super(baseShell);
        int totalPixels = getWidth() * getHeight();
        this.readyMask = new BitSet(totalPixels);
        this.replacedMask = new BitSet(totalPixels);

        if (initState == DataState.READY) this.readyMask.set(0, totalPixels, true);
        else if (initState == DataState.REPLACED) this.replacedMask.set(0, totalPixels, true);
    }

    public TSState(TSState other) {
        super(other);
        // 🚨 补丁 3：防止通过网络传过来的 JSON 缺失字段导致 null.clone() 崩溃
        this.readyMask = other.readyMask != null ? (BitSet) other.readyMask.clone() : new BitSet();
        this.replacedMask = other.replacedMask != null ? (BitSet) other.replacedMask.clone() : new BitSet();
    }

    public void mergeState(TSState incoming) {
        if (incoming == null) return;
        if (incoming.replacedMask != null && !incoming.replacedMask.isEmpty()) {
            this.replacedMask.or(incoming.replacedMask);
            this.readyMask.andNot(incoming.replacedMask);
        }
        if (incoming.readyMask != null && !incoming.readyMask.isEmpty()) {
            BitSet newReady = (BitSet) incoming.readyMask.clone();
            newReady.andNot(this.replacedMask);
            this.readyMask.or(newReady);
        }
    }

    public boolean hasHoles() {
        return (readyMask.cardinality() + replacedMask.cardinality()) < (getWidth() * getHeight());
    }

    public boolean hasReplacedData() {
        return !replacedMask.isEmpty();
    }

    // ================== 依赖父类的动态辅助 Getter ==================
    @JsonIgnore
    public int getWidth() {
        return (this.getXAxis() != null && this.getXAxis().getCount() != null) ? this.getXAxis().getCount() : 1;
    }

    @JsonIgnore
    public int getHeight() {
        return (this.getYAxis() != null && this.getYAxis().getCount() != null) ? this.getYAxis().getCount() : 1;
    }

    @JsonIgnore
    public BitSet getReadyMask() { return readyMask; }
    @JsonIgnore
    public BitSet getReplacedMask() { return replacedMask; }

    // ================== 网络传输转换 (配合拦截器使用) ==================
    @JsonProperty("readyMask")
    public long[] getReadyMaskArray() {
        return readyMask != null ? readyMask.toLongArray() : new long[0];
    }

    @JsonProperty("readyMask")
    public void setReadyMaskArray(long[] data) {
        this.readyMask = data != null ? BitSet.valueOf(data) : new BitSet();
    }

    @JsonProperty("replacedMask")
    public long[] getReplacedMaskArray() {
        return replacedMask != null ? replacedMask.toLongArray() : new long[0];
    }

    @JsonProperty("replacedMask")
    public void setReplacedMaskArray(long[] data) {
        this.replacedMask = data != null ? BitSet.valueOf(data) : new BitSet();
    }

    /**
     * 获取“空洞掩码”：true 代表该像元是空的（需要模型去算），false 代表该像元已经有合法数据了（不能被覆写）
     */
    @JsonIgnore
    public BitSet getMissingHolesMask() {
        int totalPixels = getWidth() * getHeight();
        BitSet holes = new BitSet(totalPixels);
        // 先假设全是空洞 (全置为 true)
        holes.set(0, totalPixels, true);
        // 刨除掉正常的就绪数据 (有 ready 的地方，洞被填上了)
        holes.andNot(readyMask);
        // 刨除掉人工实测的替换数据 (有 replaced 的地方，洞也被填上了)
        holes.andNot(replacedMask);
        return holes;
    }
}