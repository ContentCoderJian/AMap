package com.jlv.amap.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.services.help.Tip;
import com.jlv.amap.R;

import java.util.List;

/**
 * 搜索Adapter
 */
public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Tip> mData;
    private OnItemClickListener mListener;
    private Context mContext;

    public SearchAdapter(Context context, List<Tip> data) {
        this.mContext = context;
        this.mData = data;
    }

    public interface OnItemClickListener {
        void onItemClick(View v, int position);
    }

    /**
     * 设置RecycleView条目点击
     *
     * @param listener
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new SearchViewHolder(LayoutInflater.from(mContext).inflate(R.layout.search_tip_recycle_item, viewGroup
                , false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        Tip tip = mData.get(position);
        SearchViewHolder viewHolder = (SearchViewHolder) holder;
        viewHolder.tvSearchTitle.setText(tip.getName());
        viewHolder.tvSearchLoc.setText(tip.getAddress());
        viewHolder.rlBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onItemClick(v, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        if (mData != null && mData.size() > 0) {
            return mData.size();
        }
        return 0;
    }

    /**
     * 搜索ViewHolder
     */
    class SearchViewHolder extends RecyclerView.ViewHolder {
        TextView tvSearchTitle;
        TextView tvSearchLoc;
        RelativeLayout rlBg;

        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSearchTitle = itemView.findViewById(R.id.tv_search_title);
            tvSearchLoc = itemView.findViewById(R.id.tv_search_loc);
            rlBg = itemView.findViewById(R.id.item_bg);
        }
    }
}
