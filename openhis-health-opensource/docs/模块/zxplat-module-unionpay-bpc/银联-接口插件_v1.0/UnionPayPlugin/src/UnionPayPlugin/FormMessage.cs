using System;
using System.ComponentModel;
using System.Drawing;
using System.Windows.Forms;

namespace UnionPayPlugin;

public class FormMessage : Form
{
	private TimeSpan ts = new TimeSpan(0, 0, 19);

	private IContainer components = null;

	private GroupBox groupBox1;

	private Label lblTitle;

	private Label lblCountDown;

	private Timer timerCountDown;

	private Button btnOk;

	public FormMessage()
	{
		InitializeComponent();
	}

	public void ThisShow(string content)
	{
		lblTitle.Text = content;
		timerCountDown.Enabled = true;
	}

	public void ThisShow(string title, string content)
	{
		Text = title;
		lblTitle.Text = content;
		timerCountDown.Enabled = true;
	}

	public void ThisShow(string title, string content, bool btnenable, int closed)
	{
		Text = title;
		lblTitle.Text = content;
		btnOk.Visible = btnenable;
		if (closed >= 0)
		{
			lblCountDown.Text = closed + "s";
			lblCountDown.Visible = true;
			if (closed > 0)
			{
				closed--;
			}
			ts = new TimeSpan(0, 0, closed);
			timerCountDown.Enabled = true;
		}
		else if (closed == -1)
		{
			lblCountDown.Text = "";
			lblCountDown.Visible = false;
		}
	}

	public void CloseForm()
	{
		base.DialogResult = DialogResult.OK;
		Dispose();
		Close();
	}

	private void btnOk_Click(object sender, EventArgs e)
	{
		base.DialogResult = DialogResult.OK;
		Dispose();
		Close();
	}

	private void timerCountDown_Tick(object sender, EventArgs e)
	{
		string str = ts.TotalSeconds.ToString();
		lblCountDown.Text = str + "s";
		ts = ts.Subtract(new TimeSpan(0, 0, 1));
		if (ts.TotalSeconds < 0.0)
		{
			timerCountDown.Enabled = false;
			base.DialogResult = DialogResult.OK;
			Dispose();
			Close();
		}
	}

	protected override void Dispose(bool disposing)
	{
		if (disposing && components != null)
		{
			components.Dispose();
		}
		base.Dispose(disposing);
	}

	private void InitializeComponent()
	{
		this.components = new System.ComponentModel.Container();
		System.ComponentModel.ComponentResourceManager resources = new System.ComponentModel.ComponentResourceManager(typeof(UnionPayPlugin.FormMessage));
		this.groupBox1 = new System.Windows.Forms.GroupBox();
		this.btnOk = new System.Windows.Forms.Button();
		this.lblCountDown = new System.Windows.Forms.Label();
		this.lblTitle = new System.Windows.Forms.Label();
		this.timerCountDown = new System.Windows.Forms.Timer(this.components);
		this.groupBox1.SuspendLayout();
		base.SuspendLayout();
		this.groupBox1.BackColor = System.Drawing.Color.White;
		this.groupBox1.Controls.Add(this.btnOk);
		this.groupBox1.Controls.Add(this.lblCountDown);
		this.groupBox1.Controls.Add(this.lblTitle);
		this.groupBox1.Dock = System.Windows.Forms.DockStyle.Fill;
		this.groupBox1.Location = new System.Drawing.Point(0, 0);
		this.groupBox1.Name = "groupBox1";
		this.groupBox1.Size = new System.Drawing.Size(400, 210);
		this.groupBox1.TabIndex = 106;
		this.groupBox1.TabStop = false;
		this.btnOk.BackColor = System.Drawing.Color.FromArgb(255, 128, 0);
		this.btnOk.Cursor = System.Windows.Forms.Cursors.Default;
		this.btnOk.FlatAppearance.BorderColor = System.Drawing.Color.White;
		this.btnOk.FlatAppearance.MouseDownBackColor = System.Drawing.Color.FromArgb(255, 158, 0);
		this.btnOk.FlatStyle = System.Windows.Forms.FlatStyle.Flat;
		this.btnOk.Font = new System.Drawing.Font("宋体", 16f, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, 134);
		this.btnOk.ForeColor = System.Drawing.Color.White;
		this.btnOk.Location = new System.Drawing.Point(147, 154);
		this.btnOk.Name = "btnOk";
		this.btnOk.RightToLeft = System.Windows.Forms.RightToLeft.No;
		this.btnOk.Size = new System.Drawing.Size(106, 36);
		this.btnOk.TabIndex = 109;
		this.btnOk.Text = "确 定";
		this.btnOk.UseVisualStyleBackColor = false;
		this.btnOk.Click += new System.EventHandler(btnOk_Click);
		this.lblCountDown.Anchor = System.Windows.Forms.AnchorStyles.Bottom | System.Windows.Forms.AnchorStyles.Right;
		this.lblCountDown.Font = new System.Drawing.Font("宋体", 14f, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, 134);
		this.lblCountDown.ForeColor = System.Drawing.Color.Gray;
		this.lblCountDown.Location = new System.Drawing.Point(321, 171);
		this.lblCountDown.Name = "lblCountDown";
		this.lblCountDown.Size = new System.Drawing.Size(73, 30);
		this.lblCountDown.TabIndex = 108;
		this.lblCountDown.Text = "20s";
		this.lblCountDown.TextAlign = System.Drawing.ContentAlignment.MiddleRight;
		this.lblTitle.Anchor = System.Windows.Forms.AnchorStyles.Top | System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right;
		this.lblTitle.BackColor = System.Drawing.Color.White;
		this.lblTitle.Font = new System.Drawing.Font("宋体", 12f, System.Drawing.FontStyle.Regular, System.Drawing.GraphicsUnit.Point, 134);
		this.lblTitle.ForeColor = System.Drawing.Color.Black;
		this.lblTitle.Location = new System.Drawing.Point(3, 17);
		this.lblTitle.Name = "lblTitle";
		this.lblTitle.Size = new System.Drawing.Size(394, 110);
		this.lblTitle.TabIndex = 106;
		this.lblTitle.Text = "提示";
		this.lblTitle.TextAlign = System.Drawing.ContentAlignment.MiddleCenter;
		this.timerCountDown.Interval = 1000;
		this.timerCountDown.Tick += new System.EventHandler(timerCountDown_Tick);
		base.AutoScaleDimensions = new System.Drawing.SizeF(6f, 12f);
		base.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
		this.BackColor = System.Drawing.Color.White;
		base.ClientSize = new System.Drawing.Size(400, 210);
		base.Controls.Add(this.groupBox1);
		base.FormBorderStyle = System.Windows.Forms.FormBorderStyle.FixedSingle;
		base.Icon = (System.Drawing.Icon)resources.GetObject("$this.Icon");
		base.MaximizeBox = false;
		base.MinimizeBox = false;
		base.Name = "FormMessage";
		base.StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen;
		this.Text = "提示";
		this.groupBox1.ResumeLayout(false);
		base.ResumeLayout(false);
	}
}
