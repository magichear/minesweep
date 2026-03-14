import React from 'react';

interface HelpPanelProps {
  visible: boolean;
  onClose: () => void;
}

const HelpPanel: React.FC<HelpPanelProps> = ({ visible, onClose }) => {
  if (!visible) return null;

  return (
    <div className="stats-overlay" onClick={onClose}>
      <div className="external-panel" onClick={e => e.stopPropagation()}>
        <div className="stats-header">
          <h2>❓ Help</h2>
          <button className="close-btn" onClick={onClose}>×</button>
        </div>

        <div className="ext-section">
          <h3>GUI 使用说明</h3>
          <ul>
            <li>上方可选择难度和规则，点击笑脸可快速重开。</li>
            <li>智能辅助会高亮建议格，热力图显示每个未开格的风险概率。</li>
            <li>统计可查看历史成绩，辅助强度测试可做离线评估。</li>
            <li>其它辅助用于外部扫雷窗口识别与自动操作。</li>
          </ul>
        </div>

        <div className="ext-section">
          <h3>“其它辅助”使用说明</h3>
          <ul>
            <li>先选择难度并显示识别框，把绿色框精确覆盖到扫雷棋盘。</li>
            <li>热力图模式会自动刷新，也可点击手动刷新热力图。</li>
            <li>自动游玩会按间隔循环分析并点击，达到时限会自动停止。</li>
            <li>若打开“猜测停手”，当局面无法由已揭开信息继续扩展时会停手，交给你决策。</li>
            <li>建议在桌面背景中游玩，避免鼠标误触其他窗口。</li>
          </ul>
        </div>

        <div className="ext-section">
          <h3>扫雷玩法简介</h3>
          <ul>
            <li>左键翻开格子，右键插旗或取消旗子。</li>
            <li>数字 N 表示该格周围 8 邻域内有 N 个雷。</li>
            <li>利用数字约束逐步扩展安全区，最后翻开全部非雷格即获胜。</li>
            <li>无法确定时需要猜测，建议结合热力图选择低风险格。</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default HelpPanel;
